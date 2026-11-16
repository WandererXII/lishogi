package lila.evaluation

import lila.common.Centis
import lila.common.Maths
import lila.common.Stats

object Statistics {

  // Coefficient of Variance
  def coefVariation(a: List[Int]): Option[Float] = {
    val s = Stats(a)
    s.stdDev.map { _ / s.mean }
  }

  // ups all values by 0.1s
  // as to avoid very high variation on bullet games
  // where all move times are low
  // and drops the first move because it's always 0
  def moveTimeCoefVariation(a: List[Centis]): Option[Float] =
    coefVariation(a.drop(1).map(_.centis + 10))

  def moveTimeCoefVariationNoDrop(a: List[Centis]): Option[Float] =
    coefVariation(a.map(_.centis + 10))

  def moveTimeCoefVariation(pov: lila.game.Pov): Option[Float] =
    for {
      mt   <- moveTimesBeforeByoyomi(pov)
      coef <- moveTimeCoefVariation(mt)
    } yield coef

  def moveTimesBeforeByoyomi(pov: lila.game.Pov): Option[List[Centis]] =
    pov.game.moveTimesBeforeByoyomi(pov.color).filter(mt => mt.size >= 15)

  def cvIndicatesHighlyFlatTimes(c: Float) =
    c < 0.25

  def cvIndicatesHighlyFlatTimesForStreaks(c: Float) =
    c < 0.14

  def cvIndicatesModeratelyFlatTimes(c: Float) =
    c < 0.40

  private val instantaneous = Centis(0)

  def slidingMoveTimesCvs(pov: lila.game.Pov): Option[Iterator[Float]] =
    moveTimesBeforeByoyomi(pov) ?? {
      _.iterator
        .sliding(14)
        .map(a => a.toList.sorted.drop(1).dropRight(1))
        .filter(_.count(instantaneous ==) < 4)
        .flatMap(a => moveTimeCoefVariationNoDrop(a))
        .some
    }

  def moderatelyConsistentMoveTimes(pov: lila.game.Pov): Boolean =
    moveTimeCoefVariation(pov) ?? { cvIndicatesModeratelyFlatTimes(_) }

  private val fastMove = Centis(55)
  def noFastMoves(pov: lila.game.Pov): Boolean = {
    val moveTimes = ~pov.game.moveTimes(pov.color)
    moveTimes.count(fastMove >) <= (moveTimes.size / 20) + 2
  }

  def listAverage[T: Numeric](x: List[T]) = ~Maths.mean(x)

  def listDeviation[T: Numeric](x: List[T]) = ~Stats(x).stdDev
}
