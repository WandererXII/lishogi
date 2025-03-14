package lila.security

import play.api.mvc.RequestHeader

import org.joda.time.DateTime

import lila.common.EmailAddress
import lila.common.IpAddress
import lila.user.User

case class Dated[V](value: V, date: DateTime) extends Ordered[Dated[V]] {
  def compare(other: Dated[V]) = other.date compareTo date
  def map[X](f: V => X)        = copy(value = f(value))
  def seconds                  = date.getSeconds
}

case class AuthInfo(user: User.ID, hasFp: Boolean)

case class FingerPrintedUser(user: User, hasFingerPrint: Boolean)

case class UserSession(
    _id: String,
    ip: IpAddress,
    ua: String,
    date: Option[DateTime],
) {

  def id = _id

}

case class LocatedSession(session: UserSession, location: Option[Location])

case class IpAndFp(ip: IpAddress, fp: Option[String], user: User.ID)

case class RecaptchaPublicConfig(key: String, enabled: Boolean)

case class LameNameCheck(value: Boolean) extends AnyVal

case class UserSignup(
    user: User,
    email: EmailAddress,
    req: RequestHeader,
    fingerPrint: Option[FingerHash],
    suspIp: Boolean,
)
