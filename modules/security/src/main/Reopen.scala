package lila.security

import scala.concurrent.duration._

import play.api.i18n.Lang

import scalatags.Text.all._

import lila.common.EmailAddress
import lila.common.config._
import lila.i18n.I18nKeys.{ emails => trans }
import lila.user.User
import lila.user.UserRepo

final class Reopen(
    mailgun: Mailgun,
    userRepo: UserRepo,
    baseUrl: BaseUrl,
    tokenerSecret: Secret,
)(implicit ec: scala.concurrent.ExecutionContext) {

  import Mailgun.html._

  // Only looks at public information, so the visitor can be told what's wrong
  def findClosed(username: String): Fu[Either[(String, String), User]] =
    userRepo.byIdNotErased(User normalize username) map {
      case None => Left("noUser" -> "No account found with this username.")
      case Some(user) if user.enabled =>
        Left("alreadyActive" -> "This account is already active.")
      case Some(user) => Right(user)
    }

  // The visitor must not be told the outcome: it depends on
  // which email the account had, and on who else uses that email.
  def sendIfAllowed(
      user: User,
      email: EmailAddress,
      hasModClose: User.ID => Fu[Boolean],
  )(implicit lang: Lang): Funit =
    refusal(user, email, hasModClose) flatMap {
      case Some(code) =>
        lila.mon.user.auth.reopenRequest(code).increment()
        funit
      case None =>
        lila.mon.user.auth.reopenRequest("success").increment()
        send(user, email)
    }

  private def refusal(
      user: User,
      email: EmailAddress,
      hasModClose: User.ID => Fu[Boolean],
  ): Fu[Option[String]] =
    userRepo.enabledWithEmail(email.normalize) flatMap {
      case Some(_) => fuccess("emailUsed".some)
      case _ =>
        userRepo.prevEmail(user.id) flatMap {
          case None                                           => fuccess("noEmail".some)
          case Some(prevEmail) if !email.similarTo(prevEmail) => fuccess("differentEmail".some)
          case _ => canReopen(user, hasModClose) map { !_ option "nope" }
        }
    }

  private def send(user: User, email: EmailAddress)(implicit lang: Lang): Funit =
    tokener make user.id flatMap { token =>
      lila.mon.email.send.reopen.increment()
      val url = s"$baseUrl/account/reopen/login/$token"
      mailgun send Mailgun.Message(
        to = email,
        subject = s"Reopen your lishogi.org account: ${user.username}",
        text = s"""
${trans.passwordReset_clickOrIgnore.txt()}

$url

${trans.common_orPaste.txt()}

${Mailgun.txt.serviceNote}
""",
        htmlBody = emailMessage(
          p(trans.passwordReset_clickOrIgnore()),
          potentialAction(metaName("Log in"), Mailgun.html.url(url)),
          serviceNote,
        ).some,
      )
    }

  def confirm(token: String, hasModClose: User.ID => Fu[Boolean]): Fu[Option[User]] =
    tokener read token flatMap { _ ?? userRepo.disabledById } flatMap {
      _ ?? { user =>
        canReopen(user, hasModClose) flatMap {
          case true =>
            userRepo reopen user.id map { hasEmail =>
              if (!hasEmail) logger.warn(s"Reopened ${user.id} without email, previous email is taken")
              user.some
            }
          case _    => fuccess(none)
        }
      }
    }

  private def canReopen(user: User, hasModClose: User.ID => Fu[Boolean]): Fu[Boolean] =
    if (user.marks.alt) fuFalse
    else hasModClose(user.id) map (!_)

  private val tokener = LoginToken.makeTokener(tokenerSecret, 20 minutes)
}
