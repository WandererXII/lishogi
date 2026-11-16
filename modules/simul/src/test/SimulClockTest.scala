package lila.simul

import org.specs2.mutable.Specification

import shogi.Gote
import shogi.Sente

import lila.common.Centis
import lila.game.clock.Clock
import lila.game.clock.Timestamp
import lila.game.clock.Timestamper

class SimulClockTest extends Specification {
  "simul clocks" should {
    "grant host time with the actual starting color and allow immediate startup" in {
      val clock = SimulClock(Clock.Config(60, 0, 10, 1), 30)
        .shogiClockOf(Sente, Gote)
        .copy(timestamper = new Timestamper { def now = Timestamp(1000L) })
        .start
      clock.color must_== Gote
      clock.timer must_== Some(Timestamp(1000L))
      clock.players(Sente).remaining must_== Centis(9000)
      clock.players(Gote).remaining must_== Centis(6000)
    }
  }
}
