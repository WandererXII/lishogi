package lishogi.api

import shogi.format.{ FEN, Notation }
import lishogi.analyse.{ Analysis, Annotator }
import lishogi.game.Game
import lishogi.game.NotationDump.WithFlags
import lishogi.team.GameTeams

final class NotationDump(
    val dumper: lishogi.game.NotationDump,
    annotator: Annotator,
    simulApi: lishogi.simul.SimulApi,
    getTournamentName: lishogi.tournament.GetTourName,
    getSwissName: lishogi.swiss.GetSwissName
)(implicit ec: scala.concurrent.ExecutionContext) {

  implicit private val lang = lishogi.i18n.defaultLang

  def apply(
      game: Game,
      initialFen: Option[FEN],
      analysis: Option[Analysis],
      flags: WithFlags,
      teams: Option[GameTeams] = None,
      realPlayers: Option[RealPlayers] = None
  ): Fu[Notation] =
    dumper(game, initialFen, flags, teams) flatMap { notation =>
      if (flags.tags) (game.simulId ?? simulApi.idToName) map { simulName =>
        simulName
          .orElse(game.tournamentId flatMap getTournamentName.get)
          .orElse(game.swissId map lishogi.swiss.Swiss.Id flatMap getSwissName.apply)
          .fold(notation)(notation.withEvent)
      }
      else fuccess(notation)
    } map { notation =>
      val evaled = analysis.ifTrue(flags.evals).fold(notation)(addEvals(notation, _))
      if (flags.literate) annotator(evaled, analysis)
      else evaled
    } map { notation =>
      realPlayers.fold(notation)(_.update(game, notation))
    }

  private def addEvals(p: Notation, analysis: Analysis): Notation =
    analysis.infos.foldLeft(p) { case (notation, info) =>
      notation.updatePly(
        info.ply,
        move => {
          val comment = info.cp
            .map(_.pawns.toString)
            .orElse(info.mate.map(m => s"mate${m.value}"))
          move.copy(
            comments = comment.map(c => s"[%eval $c]").toList ::: move.comments
          )
        }
      )
    }

  def formatter(flags: WithFlags) =
    (
        game: Game,
        initialFen: Option[FEN],
        analysis: Option[Analysis],
        teams: Option[GameTeams],
        realPlayers: Option[RealPlayers]
    ) => apply(game, initialFen, analysis, flags, teams, realPlayers) dmap toNotationString

  def toNotationString(notation: Notation) = {
    s"$notation\n\n\n"
  }
}
