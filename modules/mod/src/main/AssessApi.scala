package lila.mod

import org.joda.time.DateTime
import reactivemongo.api.ReadPreference
import reactivemongo.api.bson._

import shogi.Color

import lila.analyse.Analysis
import lila.analyse.AnalysisRepo
import lila.common.ThreadLocalRandom
import lila.db.dsl._
import lila.evaluation.AccountAction
import lila.evaluation.Analysed
import lila.evaluation.Assessible
import lila.evaluation.PlayerAggregateAssessment
import lila.evaluation.PlayerAssessment
import lila.evaluation.PlayerFlags
import lila.evaluation.Statistics
import lila.game.Game
import lila.game.Player
import lila.game.Pov
import lila.game.Source
import lila.report.SuspectId
import lila.user.User

final class AssessApi(
    assessRepo: AssessmentRepo,
    modApi: ModApi,
    userRepo: lila.user.UserRepo,
    reporter: lila.hub.actors.Report,
    shoginet: lila.hub.actors.Shoginet,
    gameRepo: lila.game.GameRepo,
    analysisRepo: AnalysisRepo,
)(implicit ec: scala.concurrent.ExecutionContext) {

  private def bottomDate =
    DateTime.now.minusSeconds(3600 * 24 * 30 * 6) // matches a mongo expire index

  import PlayerFlags.playerFlagsBSONHandler

  implicit private val playerAssessmentBSONhandler: BSONDocumentHandler[PlayerAssessment] =
    Macros.handler[PlayerAssessment]

  def createPlayerAssessment(assessed: PlayerAssessment) =
    assessRepo.coll.update.one($id(assessed._id), assessed, upsert = true).void

  private def getPlayerAssessmentsByUserId(userId: String, nb: Int) =
    assessRepo.coll
      .find($doc("userId" -> userId))
      .sort($doc("date" -> -1))
      .cursor[PlayerAssessment](ReadPreference.secondaryPreferred)
      .gather[List](nb)

  private def getPlayerAggregateAssessment(
      userId: String,
      nb: Int = 100,
  ): Fu[Option[PlayerAggregateAssessment]] =
    userRepo byId userId flatMap {
      _.filter(_.noBot) ?? { user =>
        getPlayerAssessmentsByUserId(userId, nb) map { games =>
          games.nonEmpty option PlayerAggregateAssessment(user, games)
        }
      }
    }

  def withGames(pag: PlayerAggregateAssessment): Fu[PlayerAggregateAssessment.WithGames] =
    gameRepo gamesFromSecondary pag.playerAssessments.map(_.gameId) map {
      PlayerAggregateAssessment.WithGames(pag, _)
    }

  def getPlayerAggregateAssessmentWithGames(
      userId: String,
      nb: Int = 100,
  ): Fu[Option[PlayerAggregateAssessment.WithGames]] =
    getPlayerAggregateAssessment(userId, nb) flatMap {
      case None      => fuccess(none)
      case Some(pag) => withGames(pag).map(_.some)
    }

  def refreshAssessOf(user: User): Funit =
    !user.isBot ??
      (gameRepo.gamesForAssessment(user.id, 100) flatMap { gs =>
        (gs map { g =>
          analysisRepo.byGame(g) flatMap {
            _ ?? { onAnalysisReady(g, _, thenAssessUser = false) }
          }
        }).sequenceFu.void
      }) >> assessUser(user.id)

  def onAnalysisReady(game: Game, analysis: Analysis, thenAssessUser: Boolean = true): Funit =
    gameRepo holdAlerts game flatMap { holdAlerts =>
      def consistentMoveTimes(game: Game)(player: Player) =
        Statistics.moderatelyConsistentMoveTimes(Pov(game, player))
      val shouldAssess =
        if (!game.source.exists(assessableSources.contains)) false
        else if (game.mode.casual) false
        else if (Player.HoldAlert suspicious holdAlerts) true
        else if (game.isCorrespondence) false
        else if (game.playedPlies < 40) false
        else if (game.players exists consistentMoveTimes(game)) true
        else if (game.createdAt isBefore bottomDate) false
        else true
      shouldAssess.?? {
        val analysed        = Analysed(game, analysis, holdAlerts)
        val assessibleSente = Assessible(analysed, shogi.Sente)
        val assessibleGote  = Assessible(analysed, shogi.Gote)
        createPlayerAssessment(assessibleSente playerAssessment) >>
          createPlayerAssessment(assessibleGote playerAssessment)
      } >> ((shouldAssess && thenAssessUser) ?? {
        game.sentePlayer.userId.??(assessUser) >> game.gotePlayer.userId.??(assessUser)
      })
    }

  def assessUser(userId: String): Funit = {
    getPlayerAggregateAssessment(userId) flatMap {
      case Some(playerAggregateAssessment) =>
        playerAggregateAssessment.action match {
          case AccountAction.MarkEngine =>
            modApi
              .autoMark(
                SuspectId(userId),
                playerAggregateAssessment.reportText(3),
              )
          case AccountAction.Report =>
            fuccess {
              reporter ! lila.hub.actorApi.report
                .Cheater(userId, playerAggregateAssessment.reportText(3))
            }
          case AccountAction.Nothing =>
            funit
        }
      case _ => funit
    }
  }

  private val assessableSources: Set[Source] = Set(Source.Lobby, Source.Tournament)

  private def randomPercent(percent: Int): Boolean =
    ThreadLocalRandom.nextInt(100) < percent

  def onGameReady(game: Game, sente: User, gote: User): Funit = {

    import AutoAnalysis.Reason._

    def manyBlurs(player: Player) =
      game.playerBlurPercent(player.color) >= 70

    def winnerGreatProgress(player: Player): Boolean = {
      game.winner ?? (player ==)
    } && {
      player.color.fold(sente, gote).perfs(game.perfType).progress >= 100
    }

    def noFastCoefVariation(player: Player): Option[Float] =
      Statistics.noFastMoves(Pov(game, player)) ?? Statistics.moveTimeCoefVariation(
        Pov(game, player),
      )

    def winnerUserOption = game.winnerColor.map(_.fold(sente, gote))
    def winnerNbGames    = winnerUserOption.map(_.perfs(game.perfType).nb)

    def suspCoefVariation(c: Color) = {
      val x = noFastCoefVariation(game player c)
      x.filter(_ < 0.45f) orElse x.filter(_ < 0.5f).ifTrue(ThreadLocalRandom.nextBoolean())
    }
    lazy val senteSuspCoefVariation = suspCoefVariation(shogi.Sente)
    lazy val goteSuspCoefVariation  = suspCoefVariation(shogi.Gote)

    val shouldAnalyse: Fu[Option[AutoAnalysis.Reason]] =
      if (!game.analysable) fuccess(none)
      else if (!game.source.exists(assessableSources.contains)) fuccess(none)
      // give up on correspondence games
      else if (game.isCorrespondence) fuccess(none)
      // stop here for short games
      else if (game.playedPlies < 36) fuccess(none)
      // stop here for long games
      else if (game.playedPlies > 90) fuccess(none)
      // stop here for casual games
      else if (!game.mode.rated) fuccess(none)
      // discard old games
      else if (game.createdAt isBefore bottomDate) fuccess(none)
      // someone is using a bot
      else
        gameRepo holdAlerts game map { holdAlerts =>
          if (Player.HoldAlert suspicious holdAlerts) HoldAlert.some
          // sente has consistent move times
          else if (senteSuspCoefVariation.isDefined && randomPercent(70))
            senteSuspCoefVariation.map(_ => SenteMoveTime)
          // gote has consistent move times
          else if (goteSuspCoefVariation.isDefined && randomPercent(70))
            goteSuspCoefVariation.map(_ => GoteMoveTime)
          // don't analyse half of other very fast games
          else if (game.isVeryFast && randomPercent(50)) none
          // someone blurs a lot
          else if (game.players exists manyBlurs) Blurs.some
          // the winner shows a great rating progress
          else if (game.players exists winnerGreatProgress) WinnerRatingProgress.some
          // analyse some tourney games
          // else if (game.isTournament) randomPercent(20) option "Tourney random"
          /// analyse new player games
          else if (winnerNbGames.??(30 >) && randomPercent(75)) NewPlayerWin.some
          else none
        }

    shouldAnalyse map {
      _ ?? { reason =>
        lila.mon.cheat.autoAnalysis(reason.toString).increment()
        shoginet ! lila.hub.actorApi.shoginet.AutoAnalyse(game.id)
      }
    }
  }

}
