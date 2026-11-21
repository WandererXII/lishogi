package lila.round

import shogi.format.Glyphs
import shogi.format.forsyth.Sfen
import shogi.format.usi.UciToUsi
import shogi.format.usi.Usi
import shogi.format.usi.UsiCharPair
import shogi.variant.Variant

import lila.analyse.Advice
import lila.analyse.Analysis
import lila.analyse.Info
import lila.common.Centis
import lila.round.JsonView.WithFlags
import lila.tree._

object TreeBuilder {

  private type Ply = Int

  private def makeEval(info: Info) =
    Eval(
      cp = info.cp,
      mate = info.mate,
      best = info.best,
    )

  def apply(
      game: lila.game.Game,
      analysis: Option[Analysis],
      withFlags: WithFlags,
  ): Root = {
    val withClocks: Option[Vector[Centis]] = withFlags.clocks ?? game.bothClockStates
    shogi.Replay.positionsWhileValid(game.usis, game.initialSfen, game.variant) match {
      case (positionsWithInit, error) =>
        error foreach logShogiError(game.id)
        // an unreadable initial sfen yields no positions, fall back to the variant's default
        val (init, positions) = positionsWithInit match {
          case head :: tail => (head, tail)
          case Nil          => (shogi.Position(game.variant), Nil)
        }
        val sfen                = init.toSfen
        val infos: Vector[Info] = analysis.??(_.infos.toVector)
        val advices: Map[Ply, Advice] = analysis.??(
          _.advices.view
            .map { a =>
              a.ply -> a
            }
            .toMap,
        )
        val root = Root(
          ply = init.plies,
          sfen = sfen,
          check = init.check,
          clock = withFlags.clocks ?? game.clock.map { c =>
            Centis.ofSeconds(c.limitSeconds)
          },
          eval = infos lift 0 map makeEval,
        )
        def makeBranch(index: Int, p: shogi.Position, usi: Usi) = {
          val sfen   = p.toSfen
          val info   = infos lift (index - 1)
          val advice = advices get p.plies
          val branch = Branch(
            id = UsiCharPair(usi, p.variant),
            ply = p.plies,
            usi = usi,
            sfen = sfen,
            check = p.check,
            clock = withClocks flatMap (_ lift (p.plies - init.plies - 1)),
            eval = info map makeEval,
            glyphs = Glyphs.fromList(advice.map(_.judgment.glyph).toList),
            comments = Node.Comments {
              advice.map(_.makeComment(withEval = false, withBestMove = true)).toList.map { text =>
                Node.Comment(
                  Node.Comment.Id.make,
                  Node.Comment.Text(text),
                  Node.Comment.Author.Lishogi,
                )
              }
            },
          )
          advices.get(p.plies + 1).flatMap { adv =>
            positions.lift(index - 1).map { fromPosition =>
              withAnalysisChild(game.id, branch, game.variant, fromPosition.toSfen)(adv.info)
            }
          } getOrElse branch
        }
        positions.zip(game.usis).zipWithIndex.reverse match {
          case Nil => root
          case ((p, m), i) :: rest =>
            root prependChild rest.foldLeft(makeBranch(i + 1, p, m)) { case (node, ((p, m), i)) =>
              makeBranch(i + 1, p, m) prependChild node
            }
        }
    }
  }

  private def withAnalysisChild(
      id: String,
      root: Branch,
      variant: Variant,
      fromSfen: Sfen,
  )(info: Info): Branch = {
    def makeBranch(p: shogi.Position, usi: Usi) = {
      val sfen = p.toSfen
      Branch(
        id = UsiCharPair(usi, p.variant),
        ply = p.plies,
        usi = usi,
        sfen = sfen,
        check = p.check,
        eval = none,
      )
    }
    val variation = info.variation take 20
    val usis      = ~(Usi.readList(variation).orElse(UciToUsi.readList(variation)))
    shogi.Replay.positionsWhileValid(usis, fromSfen.some, variant) match {
      case (positions, error) =>
        error foreach logShogiError(id)
        positions.drop(1).zip(usis).reverse match {
          case Nil => root
          case (p, m) :: rest =>
            root addChild rest
              .foldLeft(makeBranch(p, m)) { case (node, (p, m)) =>
                makeBranch(p, m) addChild node
              }
              .setComp
        }
    }
  }

  private val logShogiError = (id: String) =>
    (err: String) =>
      logger.warn(
        s"round.TreeBuilder https://lishogi.org/$id ${err.linesIterator.toList.headOption}",
      )
}
