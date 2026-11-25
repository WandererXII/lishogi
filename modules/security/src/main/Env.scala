package lila.security

import scala.concurrent.duration._

import play.api.Configuration
import play.api.libs.ws.WSClient

import akka.actor._
import com.softwaremill.macwire._

import lila.common.Bus
import lila.common.Strings
import lila.common.config._
import lila.memo.SettingStore.Strings._
import lila.oauth.OAuthServer
import lila.user.Authenticator
import lila.user.UserRepo

@Module
final class Env(
    appConfig: Configuration,
    ws: WSClient,
    net: NetConfig,
    captcher: lila.hub.actors.Captcher,
    userRepo: UserRepo,
    authenticator: Authenticator,
    noteApi: lila.user.NoteApi,
    cacheApi: lila.memo.CacheApi,
    settingStore: lila.memo.SettingStore.Builder,
    oAuthServer: OAuthServer,
    mongoCache: lila.memo.MongoCache.Api,
    db: lila.db.Db,
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: ActorSystem,
    scheduler: Scheduler,
) {

  private val config = appConfig.get[SecurityConfig]("security")(SecurityConfig.loader)
  import net.baseUrl

  lazy val firewall = new Firewall(
    coll = db(CollName("firewall")),
    scheduler = scheduler,
  )

  lazy val flood = wire[Flood]

  lazy val rateLimits = wire[RateLimits]

  lazy val formTimer = new FormTimer(config.signupFormSecret)

  lazy val forms = wire[DataForm]

  lazy val geoIP: GeoIP = wire[GeoIP]

  lazy val userSpy = wire[UserSpyApi]

  lazy val store = new Store(db(CollName("security")), cacheApi, net.ip)

  lazy val ugcArmedSetting = settingStore[Boolean](
    "ugcArmed",
    default = true,
    text = "Enable the user garbage collector".some,
  )

  lazy val printBan = new PrintBan(db(CollName("print_ban")))

  lazy val garbageCollector = {
    def mk: (() => Boolean) => GarbageCollector = isArmed => wire[GarbageCollector]
    mk(ugcArmedSetting.get _)
  }

  private lazy val mailgun: Mailgun = wire[Mailgun]

  lazy val emailConfirm: EmailConfirm =
    if (config.emailConfirm.enabled)
      new EmailConfirmMailgun(
        userRepo = userRepo,
        mailgun = mailgun,
        baseUrl = baseUrl,
        tokenerSecret = config.emailConfirm.secret,
      )
    else wire[EmailConfirmSkip]

  lazy val passwordReset = {
    def mk = (s: Secret) => wire[PasswordReset]
    mk(config.passwordResetSecret)
  }

  lazy val magicLink = {
    def mk = (s: Secret) => wire[MagicLink]
    mk(config.loginTokenSecret)
  }

  lazy val reopen = {
    def mk = (s: Secret) => wire[Reopen]
    mk(config.passwordResetSecret)
  }

  lazy val emailChange = {
    def mk = (s: Secret) => wire[EmailChange]
    mk(config.emailChangeSecret)
  }

  lazy val automaticEmail = wire[AutomaticEmail]

  lazy val signup = wire[Signup]

  private lazy val dnsApi: DnsApi = wire[DnsApi]

  lazy val emailAddressValidator = wire[EmailAddressValidator]

  private lazy val disposableEmailDomain = new DisposableEmailDomain(
    ws = ws,
    providerUrl = config.disposableEmail.providerUrl,
  )

  // import reactivemongo.api.bson._

  lazy val spamKeywordsSetting = settingStore[Strings](
    "spamKeywords",
    default = Strings(Nil),
    text = "Spam keywords separated by a comma".some,
  )

  lazy val spam = new Spam(spamKeywordsSetting.get _)

  scheduler.scheduleOnce(30 seconds)(disposableEmailDomain.refresh())
  scheduler.scheduleWithFixedDelay(
    config.disposableEmail.refreshDelay,
    config.disposableEmail.refreshDelay,
  ) { () =>
    disposableEmailDomain.refresh()
  }

  lazy val tor: Tor = wire[Tor]
  scheduler.scheduleOnce(31 seconds)(tor.refresh.unit)
  scheduler.scheduleWithFixedDelay(config.tor.refreshDelay, config.tor.refreshDelay) { () =>
    tor.refresh flatMap firewall.unblockIps
    ()
  }

  lazy val ipTrust: IpTrust = wire[IpTrust]

  lazy val api = wire[SecurityApi]

  lazy val csrfRequestHandler = wire[CSRFRequestHandler]

  def cli = wire[Cli]

  Bus.subscribeFun("shoginet") { case lila.hub.actorApi.shoginet.NewKey(userId, key) =>
    automaticEmail.onShoginetKey(userId, key).unit
  }
}
