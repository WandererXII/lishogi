package lila.game

import scala.util.chaining._

import org.specs2.mutable._

import shogi.{ Gote, Sente }

import lila.common.Centis
import lila.db.ByteArray
import lila.game.clock.Clock

class BinaryClockTest extends Specification {

  val _0_   = "00000000"
  val since = new org.joda.time.DateTime(1600000000000L)
  val fixedTime = new lila.game.clock.Timestamper {
    def now = lila.game.clock.Timestamp(since.getMillis + 12340L)
  }
  def writeBytes(c: Clock) = BinaryFormat.clock(since) write c
  def readBytes(
      bytes: ByteArray,
      periodEntries: PeriodEntries = PeriodEntries.default,
      senteBerserk: Boolean = false,
      goteBerserk: Boolean = false,
  ): Clock =
    (BinaryFormat.clock(since).read(bytes, periodEntries, senteBerserk, goteBerserk))(Sente)
  def isomorphism(c: Clock): Clock = readBytes(writeBytes(c))

  def write(c: Clock): List[String] = writeBytes(c).showBytes.split(',').toList
  def read(bytes: List[String])     = readBytes(ByteArray.parseBytes(bytes))

  "binary Clock" should {
    val clock  = Clock(120, 2, 10, 1, Sente)
    val bits22 = List("00000010", "00000010")
    val bitsA1 = List("00001010", "00000001")
    "write" in {
      "basic clock" in {
        write(clock) must_== {
          bits22 ::: List.fill(6)(_0_) ::: bitsA1
        }
      }
      "giving 3 time" in {
        write(clock.giveTime(Sente, Centis(3))) must_== {
          bits22 ::: List("10000000", "00000000", "00000011") ::: List.fill(3)(_0_) ::: bitsA1
        }
      }
      "giving -3 time" in {
        write(clock.giveTime(Sente, Centis(-3))) must_== {
          bits22 ::: List("00000000", "00000000", "00000011") ::: List.fill(3)(_0_) ::: bitsA1
        }
      }
      "inc only" in {
        write(Clock(0, 5, 0, 0, Sente)) must_== {
          List(
            "00000000",
            "00000101",
            "10000000",
            "00000001",
            "11110100",
            "10000000",
            "00000001",
            "11110100",
            _0_,
            _0_,
          )
        }
      }
    }
    "read" in {
      "with timer" in {
        read(bits22 ::: List.fill(10)(_0_) ::: bitsA1) must_== {
          clock
        }
        read(
          bits22 ::: List("10000000", "00000000", "00000011") ::: List.fill(7)(_0_) ::: bitsA1,
        ) must_== {
          clock.giveTime(Sente, Centis(3))
        }
        read(
          bits22 ::: List("00000000", "00000000", "00000011") ::: List.fill(7)(_0_) ::: bitsA1,
        ) must_== {
          clock.giveTime(Sente, Centis(-3))
        }
      }
      "without timer bytes" in {
        read(bits22 ::: List.fill(6)(_0_) ::: bitsA1) must_== {
          clock
        }
        read(
          bits22 ::: List("10000000", "00000000", "00000011") ::: List.fill(3)(_0_) ::: bitsA1,
        ) must_== {
          clock.giveTime(Sente, Centis(3))
        }
        read(
          bits22 ::: List("00000000", "00000000", "00000011") ::: List.fill(3)(_0_) ::: bitsA1,
        ) must_== {
          clock.giveTime(Sente, Centis(-3))
        }
      }
    }
    "preserve fixed active and legacy timer bytes" in {
      val active     = clock.copy(timer = Some(lila.game.clock.Timestamp(since.getMillis + 12340L)))
      val timerBytes = List("00000000", "00000000", "00000100", "11010010")
      write(active) must_== bits22 ::: List.fill(6)(_0_) ::: timerBytes ::: bitsA1
      read(bits22 ::: List.fill(6)(_0_) ::: timerBytes ::: bitsA1).timer must_== active.timer
      val legacy = read(bits22 ::: List.fill(6)(_0_))
      legacy.byoyomiSeconds must_== 0
      legacy.timer must beNone
      val zero = clock.copy(timer = Some(lila.game.clock.Timestamp(since.getMillis)))
      write(zero) must_== bits22 ::: List.fill(9)(_0_) ::: List("00000001") ::: bitsA1
      read(write(zero)).timer.map(_.value) must_== Some(since.getMillis + 10L)
    }
    "preserve fixed berserk and period-entry representations" in {
      val berserk = Clock(600, 0, 10, 1, Sente).goBerserk(Sente)
      val bytes = List("00001010", _0_, _0_, "01110101", "00110000") :::
        List.fill(3)(_0_) ::: bitsA1
      write(berserk) must_== bytes
      val loaded = readBytes(ByteArray.parseBytes(bytes), senteBerserk = true)
      loaded.players(Sente).remaining must_== Centis(30000)
      loaded.players(Sente).periodsLeft must_== 0
      BinaryFormat.periodEntries.writeSide(Vector(1, 255, 256)).value.toVector must_==
        Vector[Byte](0, 1, 0, -1, 1, 0)
      BinaryFormat.periodEntries.readSide(ByteArray(Array[Byte](0, 1, 0, -1, 1, 0))) must_==
        Vector(1, 255, 256)
    }
    "isomorphism" in {

      "without berserk" in {
        isomorphism(clock) must_== clock

        val c2 = clock.giveTime(Sente, Centis.ofSeconds(15))
        isomorphism(c2) must_== c2

        val c3 = clock.giveTime(shogi.Gote, Centis.ofSeconds(5))
        isomorphism(c3) must_== c3

        val c4 = clock.copy(timer = Some(fixedTime.now))
        isomorphism(c4).timer.get.value must beCloseTo(c4.timer.get.value, 10L)

        Clock(120, 60, 0, 0, Sente) pipe { c =>
          isomorphism(c) must_== c
        }

        val c5 = Clock(15, 0, 10, 1, Sente)
          .giveTime(Sente, Centis.ofSeconds(-20))
          .copy(timer = Some(fixedTime.now))
        isomorphism(c5).timer.get.value must beCloseTo(c5.timer.get.value, 10L)
        isomorphism(c5).copy(timestamper = fixedTime).currentClockFor(Sente) pipe { cc =>
          cc.periods must_== 1
          cc.time.centis must beCloseTo(500, 10)
        }
      }

      "with berserk" in {
        val b1 = clock.goBerserk(Sente)
        readBytes(writeBytes(b1), senteBerserk = true) must_== b1

        val b2 = clock.giveTime(Sente, Centis(15)).goBerserk(Sente)
        readBytes(writeBytes(b2), senteBerserk = true) must_== b2

        val b3 = Clock(60, 2, 0, 0, Sente).goBerserk(Sente)
        readBytes(writeBytes(b3), senteBerserk = true) must_== b3

        val g1 = clock.goBerserk(Gote)
        readBytes(writeBytes(g1), goteBerserk = true) must_== g1

        val g2 = Clock(60, 2, 0, 0, Sente).goBerserk(Gote)
        readBytes(writeBytes(g2), goteBerserk = true) must_== g2

        val both = clock.giveTime(Gote, Centis(15)).goBerserk(Sente).goBerserk(Gote)
        readBytes(writeBytes(both), senteBerserk = true, goteBerserk = true) must_== both
      }

      "periods" in {
        val b1 = Clock(60, 0, 10, 2, Sente).updatePlayer(Sente)(_.spendPeriods(1))
        val pe = PeriodEntries.default.update(Sente, _ => Vector(5))
        readBytes(writeBytes(b1), periodEntries = pe) must_== b1
      }
    }
  }
}
