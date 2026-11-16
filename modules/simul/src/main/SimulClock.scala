package lila.simul

import shogi.Color

import lila.common.Centis
import lila.game.clock.Clock

// All durations are expressed in seconds
case class SimulClock(
    config: Clock.Config,
    hostExtraTime: Int,
) {

  def shogiClockOf(hostColor: Color, startColor: Color) =
    config.toClock(startColor).giveTime(hostColor, Centis.ofSeconds(hostExtraTime))

  def hostExtraMinutes = hostExtraTime / 60
}
