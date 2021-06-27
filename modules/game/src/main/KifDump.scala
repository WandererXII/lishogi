package lila.game

import shogi.format.Forsyth
import shogi.format.kif.{ Kifu, ParsedPgn, Parser, Tag, TagType, Tags }
import shogi.format.{ FEN, kif => shogiPgn }
import shogi.{ Centis, Color }

import lila.common.config.BaseUrl
import lila.common.LightUser

final class PgnDump(
    baseUrl: BaseUrl,
    lightUserApi: lila.user.LightUserApi
)(implicit ec: scala.concurrent.ExecutionContext) {

  import PgnDump._

  def apply(
      game: Game,
      initialFen: Option[FEN],
      flags: WithFlags,
      teams: Option[Color.Map[String]] = None
  ): Fu[Kifu] = {
    val imported = game.pgnImport.flatMap { pgni =>
      Parser.full(pgni.pgn).toOption
    }
    val tagsFuture =
      if (flags.tags) tags(game, initialFen, imported, withOpening = flags.opening, teams = teams)
      else fuccess(Tags(Nil))
    tagsFuture map { ts =>
      val moves = flags.moves ?? {
        val clocks = flags.clocks ?? ~game.bothClockStates
        val clockOffset = game.startColor.fold(0, 1)
        game.pgnMoves.zipWithIndex.map { case (san, index) =>
          shogiPgn.Move(
            ply = index + 1,
            san = san,
            secondsLeft = clocks lift (index - clockOffset) map (_.roundSeconds)
          )
        }
      }
      Kifu(ts, moves toList)
    }
  }

  private def gameUrl(id: String) = s"$baseUrl/$id"

  private def gameLightUsers(game: Game): Fu[(Option[LightUser], Option[LightUser])] =
    (game.sentePlayer.userId ?? lightUserApi.async) zip (game.gotePlayer.userId ?? lightUserApi.async)

  private def rating(p: Player) = p.rating.fold("?")(_.toString)

  def player(p: Player, u: Option[LightUser]) =
    p.aiLevel.fold(u.fold(p.name | lila.user.User.anonymous)(_.name))("lishogi AI level " + _)

  private val customStartPosition: Set[shogi.variant.Variant] =
    Set(shogi.variant.FromPosition)

  private def eventOf(game: Game) = {
    val perf = game.perfType.fold("Standard")(_.trans(lila.i18n.defaultLang))
    game.tournamentId.map { id =>
      s"${game.mode} $perf tournament https://lishogi.org/tournament/$id"
    } orElse game.simulId.map { id =>
      s"$perf simul https://lishogi.org/simul/$id"
    } getOrElse {
      s"${game.mode} $perf game"
    }
  }

  private def ratingDiffTag(p: Player, tag: Tag.type => TagType) =
    p.ratingDiff.map { rd =>
      Tag(tag(Tag), s"${if (rd >= 0) "+" else ""}$rd")
    }

  def tags(
      game: Game,
      initialFen: Option[FEN],
      imported: Option[ParsedPgn],
      withOpening: Boolean,
      teams: Option[Color.Map[String]] = None
  ): Fu[Tags] =
    gameLightUsers(game) map { case (wu, bu) =>
      Tags {
        val importedDate = imported.flatMap(_.tags(_.Date))
        List[Option[Tag]](
          Tag(
            _.Event,
            imported.flatMap(_.tags(_.Event)) | { if (game.imported) "Import" else eventOf(game) }
          ).some,
          Tag(_.Site, gameUrl(game.id)).some,
          Tag(_.Date, importedDate | Tag.UTCDate.format.print(game.createdAt)).some,
          imported.flatMap(_.tags(_.Round)).map(Tag(_.Round, _)),
          Tag(_.Sente, player(game.sentePlayer, wu)).some,
          Tag(_.Gote, player(game.gotePlayer, bu)).some,
          Tag(_.Result, result(game)).some,
          importedDate.isEmpty option Tag(
            _.UTCDate,
            imported.flatMap(_.tags(_.UTCDate)) | Tag.UTCDate.format.print(game.createdAt)
          ),
          importedDate.isEmpty option Tag(
            _.UTCTime,
            imported.flatMap(_.tags(_.UTCTime)) | Tag.UTCTime.format.print(game.createdAt)
          ),
          Tag(_.SenteElo, rating(game.sentePlayer)).some,
          Tag(_.GoteElo, rating(game.gotePlayer)).some,
          ratingDiffTag(game.sentePlayer, _.SenteRatingDiff),
          ratingDiffTag(game.gotePlayer, _.GoteRatingDiff),
          wu.flatMap(_.title).map { t =>
            Tag(_.SenteTitle, t)
          },
          bu.flatMap(_.title).map { t =>
            Tag(_.GoteTitle, t)
          },
          teams.map { t => Tag("SenteTeam", t.sente) },
          teams.map { t => Tag("GoteTeam", t.gote) },
          Tag(_.Variant, game.variant.name.capitalize).some,
          Tag.timeControl(game.clock.map(_.config)).some,
          Tag(_.ECO, game.opening.fold("?")(_.opening.eco)).some,
          withOpening option Tag(_.Opening, game.opening.fold("?")(_.opening.name)),
          Tag(
            _.Termination, {
              import shogi.Status._
              game.status match {
                case Created | Started   => "Unterminated"
                case Aborted | NoStart   => "Abandoned"
                case Timeout | Outoftime => "Time forfeit"
                case Resign |
                  Draw |
                  Stalemate |
                  Mate |
                  VariantEnd |
                  TryRule |
                  PerpetualCheck |
                  Impasse27         => "Normal"
                case Cheat              => "Rules infraction"
                case UnknownFinish      => "Unknown"
              }
            }
          ).some
        ).flatten ::: customStartPosition(game.variant).??(
          List(
            Tag(_.FEN, initialFen.fold(Forsyth.initial)(_.value)),
            Tag("SetUp", "1")
          )
        )
      }
    }
}

object PgnDump {

  case class WithFlags(
      clocks: Boolean = true,
      moves: Boolean = true,
      tags: Boolean = true,
      evals: Boolean = true,
      opening: Boolean = true,
      literate: Boolean = false,
      pgnInJson: Boolean = false,
      delayMoves: Int = 0
  )

  def result(game: Game) =
    if (game.finished) Color.showResult(game.winnerColor)
    else "*"
}
