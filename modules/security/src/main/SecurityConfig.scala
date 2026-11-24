package lila.security

import scala.concurrent.duration.FiniteDuration

import play.api.ConfigLoader

import com.softwaremill.macwire._
import io.methvin.play.autoconfig._

import lila.common.config._
import lila.security.SecurityConfig._

@Module
final private class SecurityConfig(
    @ConfigName("flood.duration") val floodDuration: FiniteDuration,
    @ConfigName("geoip") val geoIP: GeoIP.Config,
    @ConfigName("password_reset.secret") val passwordResetSecret: Secret,
    @ConfigName("email_confirm") val emailConfirm: EmailConfirm,
    @ConfigName("email_change.secret") val emailChangeSecret: Secret,
    @ConfigName("login_token.secret") val loginTokenSecret: Secret,
    @ConfigName("signup_form.secret") val signupFormSecret: Secret,
    val tor: Tor,
    @ConfigName("disposable_email") val disposableEmail: DisposableEmail,
    @ConfigName("dns_api") val dnsApi: DnsApi,
    @ConfigName("check_mail_api") val checkMail: CheckMail,
    val mailgun: Mailgun.Config,
    @ConfigName("lame_name_check") val lameNameCheck: LameNameCheck,
)

private object SecurityConfig {

  case class EmailConfirm(
      enabled: Boolean,
      secret: Secret,
  )
  implicit val emailConfirmLoader: ConfigLoader[EmailConfirm] = AutoConfig.loader[EmailConfirm]

  case class Tor(
      @ConfigName("provider_url") providerUrl: String,
      @ConfigName("refresh_delay") refreshDelay: FiniteDuration,
  )
  implicit val torLoader: ConfigLoader[Tor] = AutoConfig.loader[Tor]

  case class DisposableEmail(
      @ConfigName("provider_url") providerUrl: String,
      @ConfigName("refresh_delay") refreshDelay: FiniteDuration,
  )
  implicit val disposableLoader: ConfigLoader[DisposableEmail] = AutoConfig.loader[DisposableEmail]

  case class DnsApi(
      url: String,
      timeout: FiniteDuration,
  )
  implicit val dnsLoader: ConfigLoader[DnsApi] = AutoConfig.loader[DnsApi]

  case class CheckMail(
      url: String,
      key: Secret,
  )
  implicit val checkMailLoader: ConfigLoader[CheckMail] = AutoConfig.loader[CheckMail]

  implicit val lameNameCheckLoader: ConfigLoader[LameNameCheck] = boolLoader(LameNameCheck.apply)

  implicit val loader: ConfigLoader[SecurityConfig] = AutoConfig.loader[SecurityConfig]
}
