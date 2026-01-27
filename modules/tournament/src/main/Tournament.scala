package lila.tournament

import scala.util.chaining._

import play.api.i18n.Lang

import org.joda.time.DateTime
import org.joda.time.Duration
import org.joda.time.Interval

import shogi.Mode
import shogi.format.forsyth.Sfen

import lila.common.Animal
import lila.common.ThreadLocalRandom
import lila.rating.PerfType
import lila.user.User

case class Tournament(
    id: Tournament.ID,
    name: String,
    format: Format,
    status: Status,
    timeControl: TimeControl,
    minutes: Int,
    variant: shogi.variant.Variant,
    position: Option[Sfen],
    mode: Mode,
    password: Option[String] = None,
    candidates: List[User.ID] = Nil,
    conditions: Condition.All,
    closed: Boolean = false,
    denied: List[User.ID] = Nil,
    teamBattle: Option[TeamBattle] = None,
    candidatesOnly: Boolean = false,
    maxPlayers: Option[Int] = None,
    noBerserk: Boolean = false,
    noStreak: Boolean = false,
    proMode: Boolean = false,
    schedule: Option[Schedule],
    nbPlayers: Int,
    createdAt: DateTime,
    createdBy: User.ID,
    startsAt: DateTime,
    winnerId: Option[User.ID] = None,
    featuredId: Option[String] = None,
    spotlight: Option[Spotlight] = None,
    description: Option[String] = None,
    icon: Option[String] = None,
    hasChat: Boolean = true,
) {

  def isCreated   = status == Status.Created
  def isStarted   = status == Status.Started
  def isFinished  = status == Status.Finished
  def isEnterable = !isFinished

  def isPrivate = password.isDefined

  def isTeamBattle = teamBattle.isDefined

  def trans(implicit lang: Lang) =
    if (isUnique) name
    else if (isTeamBattle) lila.i18n.I18nKeys.tourname.xTeamBattle.txt(name)
    else schedule.fold(name)(_.trans)

  def isArena     = format == Format.Arena
  def isRobin     = format == Format.Robin
  def isOrganized = format == Format.Organized

  def hasArrangements = isRobin || isOrganized

  def notFull = nbPlayers < maxPlayersOrDefault

  def isShield = schedule.map(_.freq) has Schedule.Freq.Shield

  def isUnique = schedule.map(_.freq) has Schedule.Freq.Unique

  def isScheduled = schedule.isDefined

  def isRated = mode == Mode.Rated

  def finishesAt = startsAt plusMinutes minutes

  def secondsToStart = (startsAt.getSeconds - nowSeconds).toInt atLeast 0

  def secondsToFinish = (finishesAt.getSeconds - nowSeconds).toInt atLeast 0

  def pairingsClosed =
    secondsToFinish < math.max(30, math.min(~timeControl.clock.map(_.limitSeconds) / 2, 120))

  def candidatesFull = candidates.length > 250

  def isRecentlyFinished = isFinished && (nowSeconds - finishesAt.getSeconds) < 30 * 60

  def isRecentlyStarted = isStarted && (nowSeconds - startsAt.getSeconds) < 15

  def isNowOrSoon = startsAt.isBefore(DateTime.now plusMinutes 15) && !isFinished

  def isDistant = startsAt.isAfter(DateTime.now plusDays 1)

  def nearImportantMoment =
    secondsToStart < 60 * 60 || isRecentlyStarted || secondsToFinish < 2 * 60 * 60 || isRecentlyFinished

  def duration = new Duration(minutes.toLong * 60 * 1000)

  def interval = new Interval(startsAt, duration)

  def overlaps(other: Tournament) = interval overlaps other.interval

  def popular = nbPlayers > 3

  def maxPlayersOrDefault = maxPlayers.getOrElse(Format.maxPlayers(format))

  def perfType: PerfType = PerfType.from(variant, hasClock = !isCorrespondence)

  def isCorrespondence = timeControl.days.isDefined

  def berserkable = !noBerserk && timeControl.clock.exists(_.berserkable) && isArena
  def streakable  = !noStreak && isArena

  def clockStatus =
    secondsToFinish pipe { s =>
      "%02d:%02d".format(s / 60, s % 60)
    }

  def schedulePair = schedule map { this -> _ }

  def winner =
    winnerId map { userId =>
      Winner(
        tourId = id,
        userId = userId,
        tourName = name,
        schedule = schedule.map(_.nameKeys),
        date = finishesAt,
      )
    }

  def nonLishogiCreatedBy = (createdBy != User.lishogiId) option createdBy

  lazy val looksLikePrize = !isScheduled && lila.common.String.looksLikePrize(s"$name $description")

  override def toString =
    s"$id $startsAt $name $minutes minutes, $timeControl, $nbPlayers players"
}

object Tournament {

  type ID = String

  def make(
      by: Either[User.ID, User],
      name: Option[String],
      format: Format,
      timeControl: TimeControl,
      minutes: Int,
      variant: shogi.variant.Variant,
      position: Option[Sfen],
      mode: Mode,
      proMode: Boolean,
      password: Option[String],
      candidatesOnly: Boolean,
      maxPlayers: Option[Int],
      startDate: DateTime,
      berserkable: Boolean,
      streakable: Boolean,
      teamBattle: Option[TeamBattle],
      description: Option[String],
      icon: Option[String],
      hasChat: Boolean,
  ) =
    Tournament(
      id = makeId,
      name = name | Animal.randomName,
      format = format,
      status = Status.Created,
      timeControl = timeControl,
      minutes = minutes,
      createdBy = by.fold(identity, _.id),
      createdAt = DateTime.now,
      nbPlayers = 0,
      variant = variant,
      position = position,
      mode = mode,
      proMode = proMode,
      password = password,
      conditions = Condition.All.empty,
      teamBattle = teamBattle,
      candidatesOnly = candidatesOnly,
      maxPlayers = maxPlayers,
      noBerserk = !berserkable,
      noStreak = !streakable,
      schedule = None,
      startsAt = startDate,
      description = description,
      icon = icon,
      hasChat = hasChat,
    )

  def scheduleAs(sched: Schedule, minutes: Int) =
    Tournament(
      id = makeId,
      name = sched.trans(lila.i18n.defaultLang),
      format = sched.format,
      status = Status.Created,
      timeControl = Schedule clockFor sched,
      minutes = minutes,
      createdBy = User.lishogiId,
      createdAt = DateTime.now,
      nbPlayers = 0,
      variant = sched.variant,
      position = none,
      mode = Mode.Rated,
      proMode = false,
      conditions = sched.conditions,
      schedule = Some(sched),
      startsAt = sched.at,
    )

  def tournamentUrl(tourId: String): String = s"https://lishogi.org/tournament/$tourId"

  def makeId = ThreadLocalRandom nextString 8

  case class PastAndNext(past: List[Tournament], next: List[Tournament])
}
