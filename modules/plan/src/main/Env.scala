package lila.plan

import scala.concurrent.duration._

import play.api.Configuration

import com.softwaremill.macwire._
import io.methvin.play.autoconfig._

import lila.common.config._

@Module
private class PlanConfig(
    @ConfigName("paypal.ipn_key") val payPalIpnKey: Secret,
)

final class Env(
    appConfig: Configuration,
    db: lila.db.Db,
    timeline: lila.hub.actors.Timeline,
    notifyApi: lila.notify.NotifyApi,
    cacheApi: lila.memo.CacheApi,
    mongoCache: lila.memo.MongoCache.Api,
    lightUserApi: lila.user.LightUserApi,
    userRepo: lila.user.UserRepo,
    settingStore: lila.memo.SettingStore.Builder,
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: akka.actor.ActorSystem,
) {

  private val config = appConfig.get[PlanConfig]("plan")(AutoConfig.loader)

  private lazy val patronColl = db(CollName("plan_patron"))
  private lazy val chargeColl = db(CollName("plan_charge"))

  private lazy val notifier: PlanNotifier = wire[PlanNotifier]

  private lazy val monthlyGoalApi = new MonthlyGoalApi(
    getGoal = () => Usd(donationGoalSetting.get()),
    chargeColl = chargeColl,
  )

  val donationGoalSetting = settingStore[Int](
    "donationGoal",
    default = 100,
    text = "Monthly donation goal in USD from https://lishogi.org/costs".some,
  )

  lazy val api = new PlanApi(
    patronColl = patronColl,
    chargeColl = chargeColl,
    notifier = notifier,
    userRepo = userRepo,
    lightUserApi = lightUserApi,
    cacheApi = cacheApi,
    mongoCache = mongoCache,
    payPalIpnKey = config.payPalIpnKey,
    monthlyGoalApi = monthlyGoalApi,
  )

  private lazy val expiration = new Expiration(
    userRepo,
    patronColl,
    notifier,
  )

  system.scheduler.scheduleWithFixedDelay(15 minutes, 15 minutes) { () =>
    expiration.run.unit
  }

  def cli =
    new lila.common.Cli {
      def process = {
        case "patron" :: "lifetime" :: user :: Nil =>
          userRepo named user flatMap { _ ?? api.setLifetime } inject "ok"
        // someone donated while logged off.
        // we cannot bind the charge to the user so they get their precious wings.
        // instead, give them a free month.
        case "patron" :: "month" :: user :: Nil =>
          userRepo named user flatMap { _ ?? api.giveMonth } inject "ok"
        case "patron" :: "patreon" :: "cancel" :: user :: Nil =>
          userRepo named user flatMap { _ ?? api.unsetPatreon } inject "ok"
        case "patron" :: "patreon" :: user :: price :: Nil => {
          val p = price.toIntOption
          userRepo named user flatMap {
            case Some(u) => api.setPatreon(u, ~p) inject "ok"
            case None    => fuccess("User not found")
          }
        }
      }
    }
}
