package lila.rating

import org.goochjs.glicko2.Rating
import org.joda.time.DateTime
import reactivemongo.api.bson.BSONDocument
import reactivemongo.api.bson.BSONDocumentHandler
import reactivemongo.api.bson.Macros

import lila.db.BSON

case class Perf(
    glicko: Glicko,
    nb: Int,
    recent: List[Int],
    latest: Option[DateTime],
) {

  def intRating    = glicko.rating.toInt
  def intDeviation = glicko.deviation.toInt

  def progress: Int =
    ~recent.headOption.flatMap { head =>
      recent.lastOption map (head -)
    }

  def add(g: Glicko, date: DateTime): Perf =
    copy(
      glicko = g.cap,
      nb = nb + 1,
      recent = updateRecentWith(g),
      latest = date.some,
    )

  def add(r: Rating, date: DateTime): Option[Perf] = {
    val newGlicko = Glicko(
      rating = r.getRating
        .atMost(glicko.rating + Glicko.maxRatingDelta)
        .atLeast(glicko.rating - Glicko.maxRatingDelta),
      deviation = r.getRatingDeviation,
      volatility = r.getVolatility,
    )
    newGlicko.sanityCheck option add(newGlicko, date)
  }

  def addOrReset(monitor: lila.mon.CounterPath, msg: => String)(r: Rating, date: DateTime): Perf =
    add(r, date) | {
      lila.log("rating").error(s"Crazy Glicko2 $msg")
      monitor(lila.mon).increment()
      add(Glicko.default, date)
    }

  def averageGlicko(other: Perf) =
    copy(
      glicko = glicko average other.glicko,
    )

  def refund(points: Int): Perf = {
    val newGlicko = glicko refund points
    copy(
      glicko = newGlicko,
      recent = updateRecentWith(newGlicko),
    )
  }

  private def updateRecentWith(glicko: Glicko) =
    if (nb < 10) recent
    else (glicko.intRating :: recent) take Perf.recentMaxSize

  def clearRecent = copy(recent = Nil)

  def toRating =
    new Rating(
      math.max(Glicko.minRating, glicko.rating),
      glicko.deviation,
      glicko.volatility,
      nb,
      latest.orNull,
    )

  def isEmpty  = latest.isEmpty
  def nonEmpty = !isEmpty

  def rankable(variant: shogi.variant.Variant) = glicko.rankable(variant)
  def clueless                                 = glicko.clueless
  def provisional                              = glicko.provisional
  def established                              = glicko.established

  def showRatingProvisional = s"$intRating${provisional ?? "?"}"
}

case object Perf {

  type Key = String
  type ID  = Int

  case class Typed(perf: Perf, perfType: PerfType)

  val default = Perf(Glicko.default, 0, Nil, None)

  /* Set a latest date as a hack so that these are written to the db even though there are no games */
  val defaultManaged       = Perf(Glicko.defaultManaged, 0, Nil, DateTime.now.some)
  val defaultManagedPuzzle = Perf(Glicko.defaultManagedPuzzle, 0, Nil, DateTime.now.some)

  val recentMaxSize = 12

  // levels defeated
  case class AiLevels(standard: Option[Int], minishogi: Option[Int], kyotoshogi: Option[Int]) {
    def nonEmpty = standard.nonEmpty || minishogi.nonEmpty || kyotoshogi.nonEmpty

    def apply(v: shogi.variant.Variant): Option[Int] = v match {
      case shogi.variant.Standard   => standard
      case shogi.variant.Minishogi  => minishogi
      case shogi.variant.Kyotoshogi => kyotoshogi
      case _                        => none
    }
  }
  object AiLevels {
    val default = AiLevels(None, None, None)
  }

  case class Storm(score: Int, runs: Int) {
    def nonEmpty = runs > 0
  }

  object Storm {
    val default = Storm(0, 0)
  }

  implicit val perfBSONHandler: BSON[Perf] = new BSON[Perf] {

    import Glicko.glickoBSONHandler

    def reads(r: BSON.Reader): Perf = {
      val p = Perf(
        glicko = r.getO[Glicko]("gl") | Glicko.default,
        nb = r intD "nb",
        latest = r dateO "la",
        recent = r intsD "re",
      )
      p.copy(glicko = p.glicko.copy(deviation = Glicko.liveDeviation(p, false)))
    }

    def writes(w: BSON.Writer, o: Perf) =
      BSONDocument(
        "gl" -> o.glicko.copy(deviation = Glicko.liveDeviation(o, true)),
        "nb" -> w.int(o.nb),
        "re" -> w.listO(o.recent),
        "la" -> o.latest.map(w.date),
      )
  }

  implicit val stormBSONHandler: BSONDocumentHandler[Storm]       = Macros.handler[Storm]
  implicit val aiLevelsBSONHandler: BSONDocumentHandler[AiLevels] = Macros.handler[AiLevels]
}
