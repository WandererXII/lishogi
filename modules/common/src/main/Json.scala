package lila.common

import play.api.libs.json.{ Json => PlayJson, _ }

import org.joda.time.DateTime

import shogi.Centis
import shogi.format.forsyth.Sfen

object Json {

  def anyValWriter[O, A: Writes](f: O => A) =
    Writes[O] { o =>
      PlayJson toJson f(o)
    }

  def intAnyValWriter[O](f: O => Int): Writes[O]       = anyValWriter[O, Int](f)
  def stringAnyValWriter[O](f: O => String): Writes[O] = anyValWriter[O, String](f)

  def stringIsoWriter[O](iso: Iso[String, O]): Writes[O] = anyValWriter[O, String](iso.to)
  def intIsoWriter[O](iso: Iso[Int, O]): Writes[O]       = anyValWriter[O, Int](iso.to)

  def stringIsoReader[O](iso: Iso[String, O]): Reads[O] = Reads.of[String] map iso.from

  def intIsoFormat[O](iso: Iso[Int, O]): Format[O] =
    Format[O](
      Reads.of[Int] map iso.from,
      Writes { o =>
        JsNumber(iso to o)
      },
    )

  def stringIsoFormat[O](iso: Iso[String, O]): Format[O] =
    Format[O](
      Reads.of[String] map iso.from,
      Writes { o =>
        JsString(iso to o)
      },
    )

  implicit val centisReads: Reads[Centis] = Reads.of[Int] map Centis.apply

  implicit val jodaWrites: Writes[DateTime] = Writes[DateTime] { time =>
    JsNumber(time.getMillis)
  }

  implicit val sfenFormat: Format[Sfen] = stringIsoFormat[Sfen](Iso.sfenIso)
}
