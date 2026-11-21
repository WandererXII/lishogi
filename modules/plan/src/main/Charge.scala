package lila.plan

import org.joda.time.DateTime

case class Charge(
    _id: String, // random
    userId: Option[String],
    payPal: Option[Charge.PayPal] = none,
    cents: Cents,
    date: DateTime,
) {

  def id = _id

  def isPayPal = payPal.nonEmpty

  def serviceName =
    if (isPayPal) "paypal"
    else "???"

  def lifetimeWorthy = cents >= Cents.lifetime
}

object Charge {

  def make(
      userId: Option[String],
      payPal: Option[Charge.PayPal] = none,
      cents: Cents,
  ) =
    Charge(
      _id = lila.common.ThreadLocalRandom nextString 8,
      userId = userId,
      payPal = payPal,
      cents = cents,
      date = DateTime.now,
    )

  case class PayPal(
      ip: Option[String],
      name: Option[String],
      email: Option[String],
      txnId: Option[String],
      subId: Option[String],
  )
}
