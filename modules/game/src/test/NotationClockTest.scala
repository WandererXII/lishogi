package lila.game

import org.specs2.mutable.Specification

import shogi.format.Tag
import shogi.format.Tags

import lila.game.clock.Clock
import lila.game.clock.NotationClock

class NotationClockTest extends Specification {

  "KIF time control export" should {
    "round-trip increment and multiple byoyomi periods" in {
      val config = Clock.Config(615, 5, 30, 3)
      lila.game.clock.NotationClock
        .config(Tags(List(NotationClock.timeControlKif(Some(config))))) must beSome(config)
    }

    "round-trip a clock without byoyomi" in {
      val config = Clock.Config(600, 5, 0, 0)
      lila.game.clock.NotationClock
        .config(Tags(List(NotationClock.timeControlKif(Some(config))))) must beSome(config)
    }

    "leave untimed games without a clock" in {
      lila.game.clock.NotationClock
        .config(Tags(List(NotationClock.timeControlKif(None)))) must beNone
    }
  }
  "CSA time controls" should {
    "round-trip generated configurations" in {
      forall(
        List(
          Clock.Config(600, 0, 20, 1),
          Clock.Config(90, 5, 10, 1),
          Clock.Config(3600, 0, 0, 1),
          Clock.Config(90, 5, 0, 1),
        ),
      ) { config =>
        NotationClock.config(Tags(List(NotationClock.timeControlCsa(Some(config))))) must beSome(
          config,
        )
      }
    }
    "preserve first time-control precedence and a separate byoyomi override" in {
      val tags = Tags(
        List(Tag(_.TimeControl, "10分|30秒(3)+5秒"), Tag(_.TimeControl, "1分"), Tag(_.Byoyomi, "20秒")),
      )
      NotationClock.config(tags) must beSome(Clock.Config(600, 5, 20, 3))
    }
  }

}
