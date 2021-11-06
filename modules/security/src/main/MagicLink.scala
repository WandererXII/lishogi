package lishogi.security

import scala.concurrent.duration._
import scalatags.Text.all._

import lishogi.common.config._
import lishogi.common.EmailAddress
import lishogi.i18n.I18nKeys.{ emails => trans }
import lishogi.user.{ User, UserRepo }

final class MagicLink(
    mailgun: Mailgun,
    userRepo: UserRepo,
    baseUrl: BaseUrl,
    tokenerSecret: Secret
)(implicit ec: scala.concurrent.ExecutionContext) {

  import Mailgun.html._

  def send(user: User, email: EmailAddress): Funit =
    tokener make user.id flatMap { token =>
      lishogi.mon.email.send.magicLink.increment()
      val url           = s"$baseUrl/auth/magic-link/login/$token"
      implicit val lang = user.realLang | lishogi.i18n.defaultLang
      mailgun send Mailgun.Message(
        to = email,
        subject = trans.logInToLishogi.txt(user.username),
        text = s"""
${trans.passwordReset_clickOrIgnore.txt()}

$url

${trans.common_orPaste.txt()}

${Mailgun.txt.serviceNote}
""",
        htmlBody = emailMessage(
          p(trans.passwordReset_clickOrIgnore()),
          potentialAction(metaName("Log in"), Mailgun.html.url(url)),
          serviceNote
        ).some
      )
    }

  def confirm(token: String): Fu[Option[User]] =
    tokener read token flatMap { _ ?? userRepo.byId }

  private val tokener = LoginToken.makeTokener(tokenerSecret, 10 minutes)
}

object MagicLink {

  import scala.concurrent.duration._
  import play.api.mvc.RequestHeader
  import ornicar.scalalib.Zero
  import lishogi.memo.RateLimit
  import lishogi.common.{ HTTPRequest, IpAddress }

  private lazy val rateLimitPerIP = new RateLimit[IpAddress](
    credits = 5,
    duration = 1 hour,
    key = "email.confirms.ip"
  )

  private lazy val rateLimitPerUser = new RateLimit[String](
    credits = 3,
    duration = 1 hour,
    key = "email.confirms.user"
  )

  private lazy val rateLimitPerEmail = new RateLimit[String](
    credits = 3,
    duration = 1 hour,
    key = "email.confirms.email"
  )

  def rateLimit[A: Zero](user: User, email: EmailAddress, req: RequestHeader)(
      run: => Fu[A]
  )(default: => Fu[A]): Fu[A] =
    rateLimitPerUser(user.id, cost = 1) {
      rateLimitPerEmail(email.value, cost = 1) {
        rateLimitPerIP(HTTPRequest lastRemoteAddress req, cost = 1) {
          run
        }(default)
      }(default)
    }(default)
}
