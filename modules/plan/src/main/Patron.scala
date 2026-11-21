package lila.plan

import org.joda.time.DateTime

case class Patron(
    _id: Patron.UserId,
    payPal: Option[Patron.PayPal] = none,
    free: Option[Patron.Free] = none,
    expiresAt: Option[DateTime] = none,
    lifetime: Option[Boolean] = None,
    lastLevelUp: Option[DateTime] = None,
    patreon: Option[Boolean] = None,
) {

  def id = _id

  def userId = _id.value

  def canLevelUp = lastLevelUp.exists(_.isBefore(DateTime.now.minusDays(25)))

  def levelUpIfPossible =
    copy(
      lastLevelUp = if (canLevelUp) Some(DateTime.now) else lastLevelUp,
    )

  def expireInOneMonth: Patron =
    copy(
      expiresAt = DateTime.now.plusMonths(1).plusDays(7).some,
    )

  def removePayPal =
    copy(
      payPal = none,
      expiresAt = none,
    )

  def isDefined = payPal.isDefined

  def isLifetime = ~lifetime

  def isPatreon = ~patreon
}

object Patron {

  case class UserId(value: String) extends AnyVal

  case class PayPal(
      email: Option[PayPal.Email],
      subId: Option[PayPal.SubId],
      lastCharge: DateTime,
  ) {

    def renew = subId.isDefined
  }
  object PayPal {
    case class Email(value: String) extends AnyVal
    case class SubId(value: String) extends AnyVal
  }

  case class Free(at: DateTime)
}
