package lila.game

import shogi.Color

// times are expressed in seconds
case class CorrespondenceClock(
    increment: Int,
    senteTime: Float,
    goteTime: Float,
) {

  import CorrespondenceClock._

  def daysPerTurn = increment / 60 / 60 / 24

  def remainingTime(c: Color) = c.fold(senteTime, goteTime)

  def outoftime(c: Color) = remainingTime(c) <= 0

  def moretimeable(c: Color) = remainingTime(c) < (increment - hourSeconds)

  // in seconds
  def estimateTotalTime = increment * 40 / 2

}

private object CorrespondenceClock {

  private val hourSeconds = 60 * 60
}
