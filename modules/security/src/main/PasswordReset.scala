package lila.security

import scala.concurrent.duration._

import play.api.i18n.Lang

import scalatags.Text.all._

import lila.common.EmailAddress
import lila.common.config._
import lila.i18n.I18nKeys.{ emails => trans }
import lila.user.User
import lila.user.UserRepo

final class PasswordReset(
    mailgun: Mailgun,
    userRepo: UserRepo,
    baseUrl: BaseUrl,
    tokenerSecret: Secret,
)(implicit ec: scala.concurrent.ExecutionContext) {

  import Mailgun.html._

  def send(user: User, email: EmailAddress)(implicit lang: Lang): Funit =
    tokener make user.id flatMap { token =>
      lila.mon.email.send.resetPassword.increment()
      val url = s"$baseUrl/password/reset/confirm/$token"
      mailgun send Mailgun.Message(
        to = email,
        subject = trans.passwordReset_subject.txt(user.username),
        text = s"""
${trans.passwordReset_intro.txt()}

${trans.passwordReset_clickOrIgnore.txt()}

$url

${trans.common_orPaste.txt()}

${Mailgun.txt.serviceNote}
""",
        htmlBody = emailMessage(
          pDesc(trans.passwordReset_intro()),
          p(trans.passwordReset_clickOrIgnore()),
          potentialAction(metaName("Reset password"), Mailgun.html.url(url)),
          serviceNote,
        ).some,
      )
    }

  def confirm(token: String): Fu[Option[User]] =
    tokener read token flatMap { _ ?? userRepo.enabledById }

  // Changing either the password or the email invalidates the link
  private val tokener = new StringToken(
    secret = tokenerSecret,
    getCurrentValue = id =>
      for {
        hash  <- userRepo getPasswordHash id
        email <- userRepo email id
      } yield ~hash + email.??(_.value),
    lifetime = 1.hour.some,
  )
}

object PasswordReset {

  import scala.concurrent.duration._

  import play.api.mvc.RequestHeader

  import lila.common.HTTPRequest
  import lila.common.IpAddress
  import lila.memo.RateLimit

  private lazy val rateLimitPerIP = new RateLimit[IpAddress](
    credits = 10,
    duration = 1 hour,
    key = "password.reset.ip",
  )

  private lazy val rateLimitPerEmail = new RateLimit[String](
    credits = 3,
    duration = 1 hour,
    key = "password.reset.email",
  )

  // Keyed by the submitted email, and charged whether or not it matches an account,
  // so that being rate limited doesn't reveal which emails are registered.
  // IP goes first: a request blocked by IP must not use up the email's credits,
  // otherwise one IP could lock the owner out of resetting their password.
  def rateLimit[A](email: EmailAddress, req: RequestHeader)(
      run: => Fu[A],
  )(default: => Fu[A]): Fu[A] =
    rateLimitPerIP(HTTPRequest lastRemoteAddress req, cost = 1) {
      rateLimitPerEmail(email.normalize.value, cost = 1) {
        run
      }(default)
    }(default)
}
