package lila.security

import scala.concurrent.duration._

import scalatags.Text.all._

import lila.common.EmailAddress
import lila.common.config._
import lila.i18n.I18nKeys.{ emails => trans }
import lila.user.User
import lila.user.UserRepo

final class MagicLink(
    mailgun: Mailgun,
    userRepo: UserRepo,
    baseUrl: BaseUrl,
    tokenerSecret: Secret,
)(implicit ec: scala.concurrent.ExecutionContext) {

  import Mailgun.html._

  def send(user: User, email: EmailAddress): Funit =
    tokener make user.id flatMap { token =>
      lila.mon.email.send.magicLink.increment()
      val url           = s"$baseUrl/auth/magic-link/login/$token"
      implicit val lang = user.realLang | lila.i18n.defaultLang
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
          serviceNote,
        ).some,
      )
    }

  def confirm(token: String): Fu[Option[User]] =
    tokener read token flatMap { _ ?? userRepo.enabledById }

  private val tokener = LoginToken.makeTokener(tokenerSecret, 10 minutes)
}
