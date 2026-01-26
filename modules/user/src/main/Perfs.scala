package lila.user

import org.joda.time.DateTime

import lila.db.BSON
import lila.rating.Perf
import lila.rating.PerfType

case class Perfs(
    minishogi: Perf,
    chushogi: Perf,
    annanshogi: Perf,
    kyotoshogi: Perf,
    checkshogi: Perf,
    dobutsu: Perf,
    realTime: Perf,
    correspondence: Perf,
    puzzle: Perf,
    storm: Perf.Storm,
    aiLevels: Perf.AiLevels,
) {

  def perfs =
    List(
      "minishogi"      -> minishogi,
      "chushogi"       -> chushogi,
      "annanshogi"     -> annanshogi,
      "kyotoshogi"     -> kyotoshogi,
      "checkshogi"     -> checkshogi,
      "dobutsu"        -> dobutsu,
      "realTime"       -> realTime,
      "correspondence" -> correspondence,
      "puzzle"         -> puzzle,
    )

  def bestPerf: Option[(PerfType, Perf)] = {
    val ps = PerfType.nonPuzzle map { pt =>
      pt -> apply(pt)
    }
    val minNb = math.max(1, ps.foldLeft(0)(_ + _._2.nb) / 10)
    ps.foldLeft(none[(PerfType, Perf)]) {
      case (ro, p) if p._2.nb >= minNb =>
        ro.fold(p.some) { r =>
          Some(if (p._2.intRating > r._2.intRating) p else r)
        }
      case (ro, _) => ro
    }
  }

  def bestPerfs(nb: Int): List[(PerfType, Perf)] = {
    val ps = PerfType.nonPuzzle map { pt =>
      pt -> apply(pt)
    }
    val minNb = math.max(1, ps.foldLeft(0)(_ + _._2.nb) / 15)
    ps.filter(p => p._2.nb >= minNb).sortBy(-_._2.intRating) take nb
  }

  def bestPerfType: Option[PerfType] = bestPerf.map(_._1)

  def bestRating: Int = bestRatingIn(PerfType.leaderboardable)

  def bestStandardRating: Int = bestRatingIn(PerfType.standard)

  def bestRatingIn(types: List[PerfType]): Int = {
    val ps = types map apply match {
      case Nil => List(realTime)
      case x   => x
    }
    val minNb = ps.foldLeft(0)(_ + _.nb) / 10
    ps.foldLeft(none[Int]) {
      case (ro, p) if p.nb >= minNb =>
        ro.fold(p.intRating.some) { r =>
          Some(if (p.intRating > r) p.intRating else r)
        }
      case (ro, _) => ro
    } | Perf.default.intRating
  }

  def bestProgress: Int = bestProgressIn(PerfType.leaderboardable)

  def bestProgressIn(types: List[PerfType]): Int =
    types.foldLeft(0) { case (max, t) =>
      val p = apply(t).progress
      if (p > max) p else max
    }

  lazy val perfsMap: Map[String, Perf] = Map(
    "realTime"       -> realTime,
    "correspondence" -> correspondence,
    "minishogi"      -> minishogi,
    "chushogi"       -> chushogi,
    "annanshogi"     -> annanshogi,
    "kyotoshogi"     -> kyotoshogi,
    "checkshogi"     -> checkshogi,
    "dobutsu"        -> dobutsu,
    "puzzle"         -> puzzle,
  )

  def ratingMap: Map[String, Int] = perfsMap.view.mapValues(_.intRating).toMap

  def apply(key: String): Option[Perf] = perfsMap get key

  def apply(perfType: PerfType): Perf =
    perfType match {
      case PerfType.RealTime       => realTime
      case PerfType.Correspondence => correspondence
      case PerfType.Minishogi      => minishogi
      case PerfType.Chushogi       => chushogi
      case PerfType.Annanshogi     => annanshogi
      case PerfType.Kyotoshogi     => kyotoshogi
      case PerfType.Checkshogi     => checkshogi
      case PerfType.Dobutsu        => dobutsu
      case PerfType.Puzzle         => puzzle
    }

  def inShort =
    perfs map { case (name, perf) =>
      s"$name:${perf.intRating}"
    } mkString ", "

  def latest: Option[DateTime] =
    perfsMap.values.flatMap(_.latest).foldLeft(none[DateTime]) {
      case (None, date)                          => date.some
      case (Some(acc), date) if date isAfter acc => date.some
      case (acc, _)                              => acc
    }
}

case object Perfs {

  val default = {
    val p = Perf.default
    Perfs(
      minishogi = p,
      chushogi = p,
      annanshogi = p,
      kyotoshogi = p,
      checkshogi = p,
      dobutsu = p,
      realTime = p,
      correspondence = p,
      puzzle = p,
      storm = Perf.Storm.default,
      aiLevels = Perf.AiLevels.default,
    )
  }

  val defaultManaged = {
    val managed       = Perf.defaultManaged
    val managedPuzzle = Perf.defaultManagedPuzzle
    default.copy(
      realTime = managed,
      correspondence = managed,
      puzzle = managedPuzzle,
    )
  }

  val perfsBSONHandler = new BSON[Perfs] {

    implicit def perfHandler: BSON[Perf] = Perf.perfBSONHandler

    def reads(r: BSON.Reader): Perfs = {
      @inline def perf(key: String) = r.getO[Perf](key) getOrElse Perf.default
      Perfs(
        minishogi = perf("minishogi"),
        chushogi = perf("chushogi"),
        annanshogi = perf("annanshogi"),
        kyotoshogi = perf("kyotoshogi"),
        checkshogi = perf("checkshogi"),
        dobutsu = perf("dobutsu"),
        realTime = perf("realTime"),
        correspondence = perf("correspondence"),
        puzzle = perf("puzzle"),
        storm = r.getO[Perf.Storm]("storm") getOrElse Perf.Storm.default,
        aiLevels = r.getO[Perf.AiLevels]("ai") getOrElse Perf.AiLevels.default,
      )
    }

    private def notNew(p: Perf): Option[Perf] = p.nonEmpty option p

    def writes(w: BSON.Writer, o: Perfs) =
      reactivemongo.api.bson.BSONDocument(
        "minishogi"      -> notNew(o.minishogi),
        "chushogi"       -> notNew(o.chushogi),
        "annanshogi"     -> notNew(o.annanshogi),
        "kyotoshogi"     -> notNew(o.kyotoshogi),
        "checkshogi"     -> notNew(o.checkshogi),
        "dobutsu"        -> notNew(o.dobutsu),
        "realTime"       -> notNew(o.realTime),
        "correspondence" -> notNew(o.correspondence),
        "puzzle"         -> notNew(o.puzzle),
        "storm"          -> (o.storm.nonEmpty option o.storm),
        "ai"             -> (o.aiLevels.nonEmpty option o.aiLevels),
      )
  }

  case class Leaderboards(
      realTime: List[User.LightPerf],
      correspondence: List[User.LightPerf],
      minishogi: List[User.LightPerf],
      chushogi: List[User.LightPerf],
      annanshogi: List[User.LightPerf],
      kyotoshogi: List[User.LightPerf],
      checkshogi: List[User.LightPerf],
      dobutsu: List[User.LightPerf],
  )

}
