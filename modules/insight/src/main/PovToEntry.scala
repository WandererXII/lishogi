package lila.insight

import scala.util.chaining._
import cats.data.NonEmptyList

import shogi.format.FEN
import shogi.{ Board, Centis, Role, Stats }
import lila.analyse.{ Accuracy, Advice }
import lila.game.{ Game, Pov }

final private class PovToEntry(
    gameRepo: lila.game.GameRepo,
    analysisRepo: lila.analyse.AnalysisRepo
)(implicit ec: scala.concurrent.ExecutionContext) {

  private type Ply = Int

  case class RichPov(
      pov: Pov,
      provisional: Boolean,
      initialFen: Option[FEN],
      analysis: Option[lila.analyse.Analysis],
      division: shogi.Division,
      moveAccuracy: Option[List[Int]],
      boards: NonEmptyList[Board],
      movetimes: NonEmptyList[Centis],
      advices: Map[Ply, Advice]
  )

  def apply(game: Game, userId: String, provisional: Boolean): Fu[Either[Game, Entry]] =
    enrich(game, userId, provisional) map
      (_ flatMap convert toRight game)

  private def removeWrongAnalysis(game: Game): Boolean = {
    if (game.metadata.analysed && !game.analysable) {
      gameRepo setUnanalysed game.id
      analysisRepo remove game.id
      true
    }
    false
  }

  private def enrich(game: Game, userId: String, provisional: Boolean): Fu[Option[RichPov]] =
    if (removeWrongAnalysis(game)) fuccess(none)
    else
      lila.game.Pov.ofUserId(game, userId) ?? { pov =>
        gameRepo.initialFen(game) zip
          (game.metadata.analysed ?? analysisRepo.byId(game.id)) map { case (fen, an) =>
            for {
              boards <-
                shogi.Replay
                  .boards(
                    usis = game.usiMoves,
                    initialFen = fen,
                    variant = game.variant
                  )
                  .toOption
              movetimes <- game.moveTimes(pov.color).flatMap(_.toNel)
            } yield RichPov(
              pov = pov,
              provisional = provisional,
              initialFen = fen,
              analysis = an,
              division = shogi.Divider(boards.toList),
              moveAccuracy = an.map { Accuracy.diffsList(pov, _) },
              boards = boards,
              movetimes = movetimes,
              advices = an.?? {
                _.advices.view.map { a =>
                  a.info.ply -> a
                }.toMap
              }
            )
          }
      }

  private def makeMoves(from: RichPov): List[Move] = {
    val cpDiffs = ~from.moveAccuracy toVector
    val prevInfos = from.analysis.?? { an =>
      Accuracy.prevColorInfos(from.pov, an) pipe { is =>
        from.pov.color.fold(is, is.map(_.invert))
      }
    }
    val movetimes = from.movetimes.toList
    val roles =
      shogi.Replay.usiWithRoleWhilePossible(
        from.pov.game.usiMoves,
        from.initialFen,
        from.pov.game.variant
      ).map(_.role)
    val boards = {
      val pivot = if (from.pov.color == from.pov.game.startColor) 0 else 1
      from.boards.toList.zipWithIndex.collect {
        case (e, i) if (i % 2) == pivot => e
      }
    }
    val blurs = {
      val bools = from.pov.player.blurs.booleans
      bools ++ Array.fill(movetimes.size - bools.size)(false)
    }
    val timeCvs = slidingMoveTimesCvs(movetimes)
    movetimes.zip(roles).zip(boards).zip(blurs).zip(timeCvs).zipWithIndex.map {
      case (((((movetime, role), board), blur), timeCv), i) =>
        val ply      = i * 2 + from.pov.color.fold(1, 2)
        val prevInfo = prevInfos lift i
        val opportunism = from.advices.get(ply - 1) flatMap {
          case o if o.judgment.isBlunder =>
            from.advices get ply match {
              case Some(p) if p.judgment.isBlunder => false.some
              case _                               => true.some
            }
          case _ => none
        }
        val luck = from.advices.get(ply) flatMap {
          case o if o.judgment.isBlunder =>
            from.advices.get(ply + 1) match {
              case Some(p) if p.judgment.isBlunder => true.some
              case _                               => false.some
            }
          case _ => none
        }
        Move(
          phase = Phase.of(from.division, ply),
          tenths = movetime.roundTenths,
          role = role,
          eval = prevInfo.flatMap(_.cp).map(_.ceiled.centipawns),
          mate = prevInfo.flatMap(_.mate).map(_.moves),
          cpl = cpDiffs lift i,
          material = board.materialImbalance * from.pov.color.fold(1, -1),
          opportunism = opportunism,
          luck = luck,
          blur = blur,
          timeCv = timeCv
        )
    }
  }

  private def slidingMoveTimesCvs(movetimes: Seq[Centis]): Seq[Option[Float]] = {
    val sliding = 13 // should be odd
    val nb      = movetimes.size
    if (nb < sliding) Vector.fill(nb)(none[Float])
    else {
      val sides = Vector.fill(sliding / 2)(none[Float])
      val cvs = movetimes
        .sliding(sliding)
        .map { a =>
          // drop outliers
          coefVariation(a.map(_.centis + 10).toSeq.sorted.drop(1).dropRight(1))
        }
      sides ++ cvs ++ sides
    }
  }

  private def coefVariation(a: Seq[Int]): Option[Float] = {
    val s = Stats(a)
    s.stdDev.map { _ / s.mean }
  }

  private def bishopTrade(from: RichPov) =
    BishopTrade {
      from.division.end.fold(from.boards.last.some)(from.boards.toList.lift) match {
        case Some(board) =>
          shogi.Color.all.forall { color =>
            !board.hasPiece(shogi.Piece(color, shogi.Bishop))
          }
        case _ =>
          logger.warn(s"https://lishogi.org/${from.pov.gameId} missing endgame board")
          false
      }
    }

  private def rookTrade(from: RichPov) =
    RookTrade {
      from.division.end.fold(from.boards.last.some)(from.boards.toList.lift) match {
        case Some(board) =>
          shogi.Color.all.forall { color =>
            !board.hasPiece(shogi.Piece(color, shogi.Rook))
          }
        case _ =>
          logger.warn(s"https://lishogi.org/${from.pov.gameId} missing endgame board")
          false
      }
    }

  private def convert(from: RichPov): Option[Entry] = {
    import from._
    import pov.game
    for {
      myId     <- pov.player.userId
      myRating <- pov.player.rating
      opRating <- pov.opponent.rating
      perfType <- game.perfType
    } yield Entry(
      id = Entry povToId pov,
      number = 0, // temporary :/ the Indexer will set it
      userId = myId,
      color = pov.color,
      perf = perfType,
      eco =
        if (game.playable || game.turns < 4 || game.fromPosition || game.variant.exotic) none
        else shogi.opening.Ecopening fromGame game.usiMoves.toList,
      opponentRating = opRating,
      opponentStrength = RelativeStrength(opRating - myRating),
      moves = makeMoves(from),
      bishopTrade = bishopTrade(from),
      rookTrade = rookTrade(from),
      result = game.winnerUserId match {
        case None                 => Result.Draw
        case Some(u) if u == myId => Result.Win
        case _                    => Result.Loss
      },
      termination = Termination fromStatus game.status,
      ratingDiff = ~pov.player.ratingDiff,
      analysed = analysis.isDefined,
      provisional = provisional,
      date = game.createdAt
    )
  }
}
