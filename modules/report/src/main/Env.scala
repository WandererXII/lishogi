package lishogi.report

import akka.actor._
import com.softwaremill.macwire._
import io.methvin.play.autoconfig._
import play.api.Configuration
import scala.concurrent.duration._

import lishogi.common.config._

@Module
private class ReportConfig(
    @ConfigName("collection.report") val reportColl: CollName,
    @ConfigName("score.threshold") val scoreThreshold: Int,
    @ConfigName("actor.name") val actorName: String
)

private case class Thresholds(score: () => Int, slack: () => Int)

@Module
final class Env(
    appConfig: Configuration,
    domain: lishogi.common.config.NetDomain,
    db: lishogi.db.Db,
    isOnline: lishogi.socket.IsOnline,
    userRepo: lishogi.user.UserRepo,
    lightUserAsync: lishogi.common.LightUser.Getter,
    gameRepo: lishogi.game.GameRepo,
    securityApi: lishogi.security.SecurityApi,
    userSpyApi: lishogi.security.UserSpyApi,
    playbanApi: lishogi.playban.PlaybanApi,
    slackApi: lishogi.slack.SlackApi,
    captcher: lishogi.hub.actors.Captcher,
    fishnet: lishogi.hub.actors.Fishnet,
    settingStore: lishogi.memo.SettingStore.Builder,
    cacheApi: lishogi.memo.CacheApi
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: ActorSystem
) {

  private val config = appConfig.get[ReportConfig]("report")(AutoConfig.loader)

  private lazy val reportColl = db(config.reportColl)

  lazy val scoreThresholdSetting = settingStore[Int](
    "reportScoreThreshold",
    default = config.scoreThreshold,
    text = "Report score threshold. Reports with lower scores are concealed to moderators".some
  )

  lazy val slackScoreThresholdSetting = settingStore[Int](
    "slackScoreThreshold",
    default = 80,
    text = "Slack score threshold. Comm reports with higher scores are notified in slack".some
  )

  private val thresholds = Thresholds(
    score = scoreThresholdSetting.get _,
    slack = slackScoreThresholdSetting.get _
  )

  lazy val forms = wire[DataForm]

  private lazy val autoAnalysis = wire[AutoAnalysis]

  lazy val api = wire[ReportApi]

  lazy val modFilters = new ModReportFilter

  // api actor
  system.actorOf(
    Props(new Actor {
      def receive = {
        case lishogi.hub.actorApi.report.Cheater(userId, text) =>
          api.autoCheatReport(userId, text)
        case lishogi.hub.actorApi.report.Shutup(userId, text, major) =>
          api.autoInsultReport(userId, text, major)
        case lishogi.hub.actorApi.report.Booster(winnerId, loserId) =>
          api.autoBoostReport(winnerId, loserId)
      }
    }),
    name = config.actorName
  )

  lishogi.common.Bus.subscribeFun("playban", "autoFlag") {
    case lishogi.hub.actorApi.playban.Playban(userId, _) => api.maybeAutoPlaybanReport(userId)
    case lishogi.hub.actorApi.report.AutoFlag(suspectId, resource, text) =>
      api.autoCommFlag(SuspectId(suspectId), resource, text)
  }

  system.scheduler.scheduleWithFixedDelay(1 minute, 1 minute) { () =>
    api.inquiries.expire
  }
}
