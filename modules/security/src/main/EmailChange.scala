package lila.security

import scala.concurrent.duration._

import org.joda.time.DateTime
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
    tokener make TokenPayload(user.id, email, DateTime.now).some flatMap { token =>
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
    tokener read token dmap (_.flatten) flatMap {
      case Some(TokenPayload(userId, email, date)) if userId == me.id && !isExpired(date) =>
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

  private val tokener = new StringToken[Option[TokenPayload]](
    secret = tokenerSecret,
    getCurrentValue = p =>
      p ?? { payload =>
        userRepo email payload.userId dmap (_.??(_.value))
      },
    separator = tokenSeparator,
  )
}

object EmailChange {

  sealed trait Result
  object Result {
    case object Confirmed    extends Result
    case object EmailTaken   extends Result
    case object InvalidToken extends Result
  }

  private val lifetime = 1 hour

  private[security] val tokenSeparator = ','

  private[security] def isExpired(date: DateTime): Boolean =
    date.isBefore(DateTime.now.minusSeconds(lifetime.toSeconds.toInt))

  private[security] case class TokenPayload(userId: User.ID, email: EmailAddress, date: DateTime)

  implicit private[security] val payloadSerializable
      : StringToken.Serializable[Option[TokenPayload]] =
    new StringToken.Serializable[Option[TokenPayload]] {
      private val sep = ' '
      def read(str: String) =
        str.split(sep) match {
          case Array(userId, emailStr, dateStr) =>
            for {
              email <- EmailAddress from emailStr
              date  <- StringToken.DateStr toDate dateStr
            } yield TokenPayload(userId, email, date)
          case _ => none
        }
      def write(a: Option[TokenPayload]) =
        a ?? { case TokenPayload(userId, EmailAddress(email), date) =>
          s"$userId$sep$email$sep${StringToken.DateStr toStr date}"
        }
    }
}
