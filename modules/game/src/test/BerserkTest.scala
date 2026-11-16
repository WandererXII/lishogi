package lila.game.clock

import shogi.Gote
import shogi.Sente

class BerserkTest extends org.specs2.mutable.Specification {

  def senteBerserk(minutes: Int, seconds: Int, byo: Int) =
    Clock(minutes * 60, seconds, byo, 1, Sente)
      .goBerserk(Sente)
      .currentClockFor(Sente)
      .time
      .centis * .01

  "berserkable" should {
    "yep" in {
      Clock.Config(60 * 60, 0, 0, 1).berserkable must beTrue
      Clock.Config(1 * 60, 0, 0, 1).berserkable must beTrue
      Clock.Config(60 * 60, 60, 0, 1).berserkable must beTrue
      Clock.Config(60 * 60, 0, 60, 1).berserkable must beTrue
      Clock.Config(1 * 60, 0, 0, 1).berserkable must beTrue
    }
    "nope" in {
      Clock.Config(0 * 60, 1, 0, 1).berserkable must beFalse
      Clock.Config(0 * 60, 10, 0, 1).berserkable must beFalse
      Clock.Config(0 * 60, 0, 10, 1).berserkable must beFalse
    }
  }
  "berserk flags" should {
    "sente" in {
      Clock(60, 0, 0, 1, Sente).berserked(Sente) must beFalse
      Clock(60, 0, 0, 1, Sente).goBerserk(Sente).berserked(Sente) must beTrue
    }
    "gote" in {
      Clock(60, 0, 0, 1, Sente).berserked(Gote) must beFalse
      Clock(60, 0, 0, 1, Sente).goBerserk(Gote).berserked(Gote) must beTrue
    }
  }
  "initial time penalty, no byoyomi, no increment" should {
    "10+0" in {
      senteBerserk(10, 0, 0) must_== 5 * 60
    }
    "5+0" in {
      senteBerserk(5, 0, 0) must_== 2.5 * 60
    }
    "3+0" in {
      senteBerserk(3, 0, 0) must_== 1.5 * 60
    }
    "1+0" in {
      senteBerserk(1, 0, 0) must_== 0.5 * 60
    }
  }
  "initial time penalty, no byoyomi, with increment" should {
    "4+4" in {
      senteBerserk(4, 4, 0) must_== 2 * 60
    }
    "3+2" in {
      senteBerserk(3, 2, 0) must_== 1.5 * 60
    }
    "2+10" in {
      senteBerserk(2, 10, 0) must_== 2 * 60
    }
    "10+5" in {
      senteBerserk(10, 5, 0) must_== 5 * 60
    }
    "10+2" in {
      senteBerserk(10, 2, 0) must_== 5 * 60
    }
    "1+1" in {
      senteBerserk(1, 1, 0) must_== 0.5 * 60
    }
    "1+3" in {
      senteBerserk(1, 3, 0) must_== 1 * 60
    }
    "1+5" in {
      senteBerserk(1, 5, 0) must_== 1 * 60
    }
  }
  "initial time penalty, with byoyomi, no increment" should {
    "3|2" in {
      senteBerserk(3, 0, 2) must_== 1.5 * 60
    }
  }
}
