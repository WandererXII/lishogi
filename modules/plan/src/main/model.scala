package lila.plan

case class Usd(value: BigDecimal) extends AnyVal with Ordered[Usd] {
  def compare(other: Usd) = value compare other.value
  def cents               = Cents((value * 100).toInt)
  override def toString   = s"$$$value"
}
object Usd {
  def apply(value: Int): Usd = Usd(BigDecimal(value))
}
case class Cents(value: Int) extends AnyVal with Ordered[Cents] {
  def compare(other: Cents) = Integer.compare(value, other.value)
  def usd                   = Usd(BigDecimal(value, 2))
  override def toString     = usd.toString
}

object Cents {
  val lifetime = Cents(25000)

  val defaultAmounts = List(5, 10, 20, 50).map(Usd.apply).map(_.cents)
}
