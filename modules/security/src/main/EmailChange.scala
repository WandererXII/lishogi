package lila.security

import scala.concurrent.duration._

import scalatags.Text.all._

import lila.common.EmailAddress
import lila.common.config._
import lila.i18n.I18nKeys.{ emails => trans }
import lila.user.User
import lila.user.UserRepo

final class EmailChange(
    userRepo: UserRepo,
    mailgun: Mailgun,
    baseUrl: BaseUrl,
    tokenerSecret: Secret,
)(implicit ec: scala.concurrent.ExecutionContext) {

  import EmailChange._
  import Mailgun.html._

  def send(user: User, email: EmailAddress): Funit =
    tokener make writePayload(user.id, email) flatMap { token =>
      lila.mon.email.send.change.increment()
      implicit val lang = user.realLang | lila.i18n.defaultLang
      val url           = s"$baseUrl/account/email/confirm/$token"
      lila.log("auth").info(s"Change email URL ${user.username} $email $url")
      mailgun send Mailgun.Message(
        to = email,
        subject = trans.emailChange_subject.txt(user.username),
        text = s"""
${trans.emailChange_intro.txt()}
${trans.emailChange_click.txt()}

$url

${trans.common_orPaste.txt()}

${Mailgun.txt.serviceNote}
""",
        htmlBody = emailMessage(
          pDesc(trans.emailChange_intro()),
          p(trans.emailChange_click()),
          potentialAction(metaName("Change email address"), Mailgun.html.url(url)),
          serviceNote,
        ).some,
      )
    }

  // Opening the link proves access to the new address, not ownership of the account
  def confirm(me: User, token: String): Fu[Result] =
    tokener read token dmap (_ flatMap readPayload) flatMap {
      case Some((userId, email)) if userId == me.id =>
        userRepo.idByEmail(email.normalize) flatMap {
          case Some(id) if id != me.id => fuccess(Result.EmailTaken)
          case _ =>
            userRepo
              .setEmail(me.id, email)
              .inject(Result.Confirmed: Result)
              .recover(lila.db.recoverDuplicateKey(_ => Result.EmailTaken)) // lost a race
        }
      case _ => fuccess(Result.InvalidToken)
    }

  private val tokener = new StringToken(
    secret = tokenerSecret,
    getCurrentValue = payload =>
      readPayload(payload) ?? { case (userId, _) =>
        userRepo email userId dmap (_.??(_.value))
      },
    lifetime = 1.hour.some,
  )
}

object EmailChange {

  sealed trait Result
  object Result {
    case object Confirmed    extends Result
    case object EmailTaken   extends Result
    case object InvalidToken extends Result
  }

  // can't be in a user id or an email
  private[security] val payloadSeparator = ' '

  private[security] def writePayload(userId: User.ID, email: EmailAddress): String =
    s"$userId$payloadSeparator${email.value}"

  private[security] def readPayload(payload: String): Option[(User.ID, EmailAddress)] =
    payload split payloadSeparator match {
      case Array(userId, email) => EmailAddress from email map { userId -> _ }
      case _                    => none
    }
}
