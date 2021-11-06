package lishogi.mod

import akka.actor._
import com.softwaremill.macwire._
import io.methvin.play.autoconfig._
import play.api.Configuration

import lishogi.common.config._
import lishogi.user.User

@Module
private class ModConfig(
    @ConfigName("collection.player_assessment") val assessmentColl: CollName,
    @ConfigName("collection.boosting") val boostingColl: CollName,
    @ConfigName("collection.modlog") val modlogColl: CollName,
    @ConfigName("collection.gaming_history") val gamingHistoryColl: CollName,
    @ConfigName("actor.name") val actorName: String,
    @ConfigName("boosting.nb_games_to_mark") val boostingNbGamesToMark: Int,
    @ConfigName("boosting.ratio_games_to_mark") val boostingRatioToMark: Int
)

@Module
final class Env(
    appConfig: Configuration,
    db: lishogi.db.Db,
    reporter: lishogi.hub.actors.Report,
    fishnet: lishogi.hub.actors.Fishnet,
    perfStat: lishogi.perfStat.Env,
    reportApi: lishogi.report.ReportApi,
    lightUserApi: lishogi.user.LightUserApi,
    securityApi: lishogi.security.SecurityApi,
    tournamentApi: lishogi.tournament.TournamentApi,
    gameRepo: lishogi.game.GameRepo,
    analysisRepo: lishogi.analyse.AnalysisRepo,
    userRepo: lishogi.user.UserRepo,
    simulEnv: lishogi.simul.Env,
    chatApi: lishogi.chat.ChatApi,
    notifyApi: lishogi.notify.NotifyApi,
    historyApi: lishogi.history.HistoryApi,
    rankingApi: lishogi.user.RankingApi,
    noteApi: lishogi.user.NoteApi,
    cacheApi: lishogi.memo.CacheApi,
    slackApi: lishogi.slack.SlackApi
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: ActorSystem
) {

  private val config = appConfig.get[ModConfig]("mod")(AutoConfig.loader)

  private def scheduler = system.scheduler

  private lazy val logRepo        = new ModlogRepo(db(config.modlogColl))
  private lazy val assessmentRepo = new AssessmentRepo(db(config.assessmentColl))
  private lazy val historyRepo    = new HistoryRepo(db(config.gamingHistoryColl))

  lazy val logApi = wire[ModlogApi]

  lazy val impersonate = wire[ImpersonateApi]

  private lazy val notifier = wire[ModNotifier]

  private lazy val ratingRefund = wire[RatingRefund]

  lazy val publicChat = wire[PublicChat]

  lazy val api: ModApi = wire[ModApi]

  private lazy val boosting = new BoostingApi(
    modApi = api,
    collBoosting = db(config.boostingColl),
    nbGamesToMark = config.boostingNbGamesToMark,
    ratioGamesToMark = config.boostingRatioToMark
  )

  lazy val assessApi = wire[AssessApi]

  lazy val gamify = wire[Gamify]

  lazy val search = wire[UserSearch]

  lazy val inquiryApi = wire[InquiryApi]

  lazy val stream = wire[ModStream]

  // api actor
  lishogi.common.Bus.subscribe(
    system.actorOf(
      Props(new Actor {
        def receive = {
          case lishogi.analyse.actorApi.AnalysisReady(game, analysis) =>
            assessApi.onAnalysisReady(game, analysis)
          case lishogi.game.actorApi.FinishGame(game, senteUserOption, goteUserOption) if !game.aborted =>
            (senteUserOption |@| goteUserOption) apply { case (senteUser, goteUser) =>
              boosting.check(game, senteUser, goteUser) >>
                assessApi.onGameReady(game, senteUser, goteUser)
            }
            if (game.status == shogi.Status.Cheat)
              game.loserUserId foreach { logApi.cheatDetected(_, game.id) }
          case lishogi.hub.actorApi.mod.ChatTimeout(mod, user, reason, text) =>
            logApi.chatTimeout(mod, user, reason, text)
          case lishogi.hub.actorApi.security.GCImmediateSb(userId) =>
            reportApi getSuspect userId orFail s"No such suspect $userId" flatMap { sus =>
              reportApi.getLishogiMod map { mod =>
                api.setTroll(mod, sus, true)
              }
            }
          case lishogi.hub.actorApi.security.GarbageCollect(userId) =>
            reportApi getSuspect userId orFail s"No such suspect $userId" flatMap { sus =>
              api.garbageCollect(sus) >> publicChat.delete(sus)
            }
          case lishogi.hub.actorApi.mod.AutoWarning(userId, subject) =>
            logApi.modMessage(User.lishogiId, userId, subject)
        }
      }),
      name = config.actorName
    ),
    "finishGame",
    "analysisReady",
    "garbageCollect",
    "playban",
    "autoWarning"
  )
}
