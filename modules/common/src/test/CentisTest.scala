package lila.common

import org.specs2.mutable.Specification

class CentisTest extends Specification {

  "integer wire units" should {
    "retain JSON centiseconds and the integer isomorphism" in {
      val centis = Centis(12345)
      play.api.libs.json.JsNumber(12345).as[Centis](Json.centisReads) must_== centis
      Iso.centisIso.to(centis) must_== 12345
      Iso.centisIso.from(-12345) must_== Centis(-12345)
    }
  }

  "millisecond conversion" should {
    "match the previous Int formula across boundaries and sampled inputs" in {
      val random = new scala.util.Random(0L)
      val inputs = (-10000 to 10000) ++
        (0 to 20).flatMap(offset => Seq(Int.MinValue + offset, Int.MaxValue - offset)) ++
        Vector.fill(10000)(random.nextInt())

      val mismatch = inputs.find { millis =>
        val before = Centis(
          ((if (millis > 0) millis.toLong + 5 else millis.toLong - 4) / 10).toInt,
        )
        Centis.ofMillis(millis) != before
      }
      mismatch must beNone
    }

    "match the previous Long formula across boundaries and sampled inputs" in {
      def before(millis: Long): Centis =
        if (millis >= Int.MaxValue.toLong * 10) Centis(Int.MaxValue)
        else if (millis <= Int.MinValue.toLong * 10) Centis(Int.MinValue)
        else Centis(((if (millis > 0) millis + 5 else millis - 4) / 10).toInt)

      val random = new scala.util.Random(1L)
      val boundaries = Seq(
        0L,
        Int.MinValue.toLong,
        Int.MaxValue.toLong,
        Int.MinValue.toLong * 10,
        Int.MaxValue.toLong * 10,
        Long.MinValue + 20,
        Long.MaxValue - 20,
      ).flatMap(boundary => (-20 to 20).map(offset => boundary + offset.toLong))
      val inputs = boundaries ++
        (-10000L to 10000L) ++
        Vector.fill(10000)(random.nextInt().toLong * 10 + random.nextInt(21).toLong - 10) ++
        Vector.fill(10000)(random.nextLong())

      val mismatch = inputs.find { millis =>
        Centis.ofMillis(millis) != before(millis)
      }
      mismatch must beNone
    }

    "round extreme Int inputs without overflow" in {
      Centis.ofMillis(Int.MaxValue) mustEqual Centis(214748365)
      Centis.ofMillis(Int.MinValue) mustEqual Centis(-214748365)
    }

    "saturate extreme Long inputs with the correct sign" in {
      Centis.ofMillis(Long.MaxValue) mustEqual Centis(Int.MaxValue)
      Centis.ofMillis(Long.MinValue) mustEqual Centis(Int.MinValue)
    }

    "preserve rounding at zero and halfway points for both overloads" in {
      forall(
        Seq(
          -16 -> -2,
          -15 -> -1,
          -6  -> -1,
          -5  -> 0,
          -4  -> 0,
          0   -> 0,
          4   -> 0,
          5   -> 1,
          6   -> 1,
          15  -> 2,
        ),
      ) { case (millis, centis) =>
        Centis.ofMillis(millis) mustEqual Centis(centis)
        Centis.ofMillis(millis.toLong) mustEqual Centis(centis)
      }
    }

    "round Long inputs at the saturation boundaries" in {
      forall(
        Seq(
          21474836464L  -> 2147483646,
          21474836465L  -> Int.MaxValue,
          21474836475L  -> Int.MaxValue,
          -21474836475L -> -2147483647,
          -21474836476L -> Int.MinValue,
          -21474836486L -> Int.MinValue,
        ),
      ) { case (millis, centis) =>
        Centis.ofMillis(millis) mustEqual Centis(centis)
      }
    }
  }

  "roundTenths" should {
    "round extreme values without overflow" in {
      Centis(Int.MaxValue).roundTenths mustEqual 214748365
      Centis(Int.MinValue).roundTenths mustEqual -214748365
    }

    "preserve rounding toward positive infinity at halfway points" in {
      forall(
        Seq(
          -16 -> -2,
          -15 -> -1,
          -6  -> -1,
          -5  -> 0,
          -4  -> 0,
          0   -> 0,
          4   -> 0,
          5   -> 1,
          6   -> 1,
          15  -> 2,
        ),
      ) { case (centis, tenths) =>
        Centis(centis).roundTenths mustEqual tenths
      }
    }
  }

  "averaging" should {
    "preserve large positive and negative values" in {
      forall(Seq(1500000000, -1500000000, Int.MaxValue, Int.MinValue)) { value =>
        Centis(value).avg(Centis(value)) mustEqual Centis(value)
      }
    }

    "preserve rounding down for odd sums" in {
      Centis(2).avg(Centis(3)) mustEqual Centis(2)
      Centis(-2).avg(Centis(-3)) mustEqual Centis(-3)
      Centis(Int.MaxValue).avg(Centis(Int.MinValue)) mustEqual Centis(-1)
    }
  }
}
