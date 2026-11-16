package lila.tournament

import play.api.i18n.Lang

import org.joda.time.DateTime

import shogi.variant.Variant

import lila.i18n.I18nKeys
import lila.rating.PerfType

case class Schedule(
    format: Format,
    freq: Schedule.Freq,
    speed: Schedule.Speed,
    variant: Variant,
    at: DateTime,
    conditions: Condition.All = Condition.All.empty,
) {

  def trans(implicit lang: Lang): String = {
    val variantSpeedTrans = PerfType
      .byVariant(variant)
      .map(
        _.trans,
      ) | Schedule.Speed.standardTrans(speed)
    val freqTrans = Schedule.Freq.trans(freq, variantSpeedTrans)

    if (format == Format.Arena) I18nKeys.tourname.xArena.txt(freqTrans)
    else if (format == Format.Robin) I18nKeys.tourname.xRobin.txt(freqTrans)
    else freqTrans
  }

  def nameKeys = List(format.key, freq.key, speed.key, variant.key).mkString(" ")

  def day = at.withTimeAtStartOfDay

  def sameVariant(other: Schedule) = variant.id == other.variant.id

  def sameFreq(other: Schedule) = freq == other.freq

  def similarConditions(other: Schedule) = conditions similar other.conditions

  def sameDay(other: Schedule) = day == other.day

  def hasMaxRating = conditions.maxRating.isDefined

  def similarTo(other: Schedule) =
    sameVariant(other) && sameFreq(other)

  def perfType = PerfType.byVariant(variant) | {
    if (speed == Schedule.Speed.Correspondence) PerfType.Correspondence
    else PerfType.RealTime
  }

  def plan                                  = Schedule.Plan(this, None)
  def plan(build: Tournament => Tournament) = Schedule.Plan(this, build.some)

  override def toString = s"$freq $variant $speed $conditions $at"
}

object Schedule {

  def fromNameKeys(keys: String): Option[Schedule] = {
    val ks = keys.split(" ")
    for {
      format  <- ks.lift(0).flatMap(Format.byKey)
      freq    <- ks.lift(1).flatMap(Freq.apply)
      speed   <- ks.lift(2).flatMap(Speed.apply)
      variant <- ks.lift(3).flatMap(Variant.apply)
    } yield Schedule(
      format = format,
      freq = freq,
      speed = speed,
      variant = variant,
      at = DateTime.now, // whatever
    )
  }

  case class Plan(schedule: Schedule, buildFunc: Option[Tournament => Tournament]) {

    def build: Tournament = {
      val t = Tournament.scheduleAs(addCondition(schedule), durationFor(schedule))
      buildFunc.foldRight(t) { _(_) }
    }

    def map(f: Tournament => Tournament) =
      copy(
        buildFunc = buildFunc.fold(f)(f.compose).some,
      )
  }

  sealed abstract class Freq(val id: Int, val importance: Int) extends Ordered[Freq] {

    val key = toString.toLowerCase

    def compare(other: Freq) = Integer.compare(importance, other.importance)

    def isDaily          = this == Schedule.Freq.Daily
    def isDailyOrBetter  = this >= Schedule.Freq.Daily
    def isWeeklyOrBetter = this >= Schedule.Freq.Weekly
  }
  object Freq {
    case object Hourly  extends Freq(10, 10)
    case object Daily   extends Freq(20, 20)
    case object Eastern extends Freq(30, 15)
    case object Weekly  extends Freq(40, 40)
    case object Weekend extends Freq(41, 41)
    case object Monthly extends Freq(50, 50)
    case object Shield  extends Freq(51, 51)
    case object Yearly  extends Freq(70, 70)
    case object Unique  extends Freq(90, 59)
    val all: List[Freq] = List(
      Hourly,
      Daily,
      Eastern,
      Weekly,
      Weekend,
      Monthly,
      Shield,
      Yearly,
      Unique,
    )
    def apply(key: String) = all.find(_.key == key)
    def byId(id: Int)      = all.find(_.id == id)

    def trans(freq: Freq, x: String)(implicit lang: Lang) =
      freq match {
        case Schedule.Freq.Hourly  => I18nKeys.tourname.hourlyX.txt(x)
        case Schedule.Freq.Daily   => I18nKeys.tourname.dailyX.txt(x)
        case Schedule.Freq.Eastern => I18nKeys.tourname.easternX.txt(x)
        case Schedule.Freq.Weekly  => I18nKeys.tourname.weeklyX.txt(x)
        case Schedule.Freq.Weekend => I18nKeys.tourname.weekendX.txt(x)
        case Schedule.Freq.Monthly => I18nKeys.tourname.monthlyX.txt(x)
        case Schedule.Freq.Yearly  => I18nKeys.tourname.yearlyX.txt(x)
        case Schedule.Freq.Shield  => I18nKeys.tourname.xShield.txt(x)
        case _                     => s"${freq.key} $x"
      }
  }

  sealed abstract class Speed(val factor: Int) {
    val key = lila.common.String lcfirst toString
  }
  object Speed {
    case object VeryFast       extends Speed(20)
    case object Fast           extends Speed(40)
    case object Normal         extends Speed(60)
    case object Correspondence extends Speed(80)

    val all: List[Speed] =
      List(
        VeryFast,
        Fast,
        Normal,
        Correspondence,
      )

    def apply(key: String) =
      all.find(_.key == key) orElse all.find(_.key.toLowerCase == key.toLowerCase)
    def closest(factor: Int) =
      all.minBy(s => math.abs(s.factor - factor))

    def fromClock(clock: lila.game.clock.Clock.Config) = {
      val time = clock.estimateTotalSeconds
      if (time < 600) VeryFast
      else if (time < 1500) Fast
      else Normal
    }

    def standardTrans(speed: Speed)(implicit lang: Lang) =
      speed match {
        case VeryFast       => I18nKeys.veryFastShogi.txt()
        case Fast           => I18nKeys.fastShogi.txt()
        case Normal         => I18nKeys.shogi.txt()
        case Correspondence => I18nKeys.correspondence.txt()
      }
  }

  private[tournament] def durationFor(s: Schedule): Int = {
    import Freq._, Speed._
    import shogi.variant._

    (s.freq, s.speed, s.variant) match {
      case (Hourly, _, _) => 57

      case (Daily | Eastern, VeryFast, _) => 90
      case (Daily | Eastern, Fast, _)     => 120
      case (Daily | Eastern, Normal, _)   => 150

      case (Weekly | Weekend, VeryFast, _) => 60 * 2
      case (Weekly | Weekend, Fast, _)     => 60 * 3
      case (Weekly | Weekend, Normal, _)   => 60 * 4

      case (Monthly, _, Minishogi | Kyotoshogi) => 60 * 2
      case (Monthly, VeryFast, _)               => 60 * 2
      case (Monthly, Fast, _)                   => 60 * 3
      case (Monthly, Normal, _)                 => 60 * 4

      case (Shield | Yearly, _, Minishogi | Kyotoshogi) => 60 * 3
      case (Shield | Yearly, VeryFast, _)               => 60 * 3
      case (Shield | Yearly, Fast, _)                   => 60 * 5
      case (Shield | Yearly, Normal, _)                 => 60 * 7

      case (Unique, _, _) => 60 * 6
      case _              => 60
    }
  }

  private[tournament] def clockFor(s: Schedule) = {
    import Speed._
    import shogi.variant._

    val CC = lila.game.clock.Clock.Config
    val RT = TimeControl.RealTime
    val CR = TimeControl.Correspondence

    (s.speed, s.variant) match {
      // small board time controls
      case (Correspondence, Minishogi | Kyotoshogi) => CR(3)
      case (Fast, Minishogi | Kyotoshogi)           => RT(CC(5 * 60, 0, 10, 1))
      case (Normal, Minishogi | Kyotoshogi)         => RT(CC(10 * 60, 0, 15, 1))
      // normal board time controls
      case (Correspondence, _) => CR(7)
      case (VeryFast, _)       => RT(CC(0, 0, 10, 1))
      case (Fast, _)           => RT(CC(10 * 60, 0, 10, 1))
      case (Normal, _)         => RT(CC(15 * 60, 0, 30, 1))
    }
  }

  private[tournament] def addCondition(s: Schedule) =
    s.copy(conditions = conditionFor(s))

  private[tournament] def conditionFor(s: Schedule) =
    if (s.conditions.relevant) s.conditions
    else {
      import Freq._

      val nbRatedGame = s.freq match {
        case Hourly | Daily | Eastern => 0
        case Weekend | Monthly        => 3
        case Shield | Yearly          => 10
        case _                        => 0
      }

      Condition.All(
        nbRatedGame = nbRatedGame.some.filter(0 <).map {
          Condition.NbRatedGame(_)
        },
        minRating = none,
        maxRating = none,
        titled = none,
        teamMember = none,
      )
    }
}
