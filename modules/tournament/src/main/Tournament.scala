package lila.tournament

import scala.util.chaining._

import play.api.i18n.Lang

import org.joda.time.DateTime
import org.joda.time.Duration
import org.joda.time.Interval

import shogi.Mode
import shogi.Speed
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
    schedule: Option[Schedule],
    nbPlayers: Int,
    createdAt: DateTime,
    createdBy: User.ID,
    startsAt: DateTime,
    winnerId: Option[User.ID] = None,
    featuredId: Option[String] = None,
    spotlight: Option[Spotlight] = None,
    description: Option[String] = None,
    hasChat: Boolean = true,
) {

  def isCreated   = status == Status.Created
  def isStarted   = status == Status.Started
  def isFinished  = status == Status.Finished
  def isEnterable = !isFinished

  def isPrivate = password.isDefined

  def isTeamBattle = teamBattle.isDefined

  def trans(implicit lang: Lang) =
    if (isMarathon || isUnique) name
    else if (isTeamBattle) lila.i18n.I18nKeys.tourname.xTeamBattle.txt(name)
    else schedule.fold(name)(_.trans)

  def isArena     = format == Format.Arena
  def isRobin     = format == Format.Robin
  def isOrganized = format == Format.Organized

  def hasArrangements = isRobin || isOrganized

  def notFull = nbPlayers < maxPlayersOrDefault

  def isMarathon =
    schedule.map(_.freq) exists {
      case Schedule.Freq.Marathon => true
      case _                      => false
    }

  def isShield = schedule.map(_.freq) has Schedule.Freq.Shield

  def isUnique = schedule.map(_.freq) has Schedule.Freq.Unique

  def isMarathonOrUnique = isMarathon || isUnique

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

  def duration = new Duration(minutes.toLong * 60 * 1000)

  def interval = new Interval(startsAt, duration)

  def overlaps(other: Tournament) = interval overlaps other.interval

  def similarTo(other: Tournament) =
    (schedule, other.schedule) match {
      case (Some(s1), Some(s2)) if s1 similarTo s2 => true
      case _                                       => false
    }

  def popular = nbPlayers > 3

  def maxPlayersOrDefault = maxPlayers.getOrElse(Format.maxPlayers(format))

  def speed = timeControl.clock.map(Speed.apply).getOrElse(Speed.Correspondence)

  def perfType: PerfType = PerfType(variant, speed)

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

case class EnterableTournaments(tours: List[Tournament], scheduled: List[Tournament])

object Tournament {

  type ID = String

  val minPlayers = 2

  def make(
      by: Either[User.ID, User],
      name: Option[String],
      format: Format,
      timeControl: TimeControl,
      minutes: Int,
      variant: shogi.variant.Variant,
      position: Option[Sfen],
      mode: Mode,
      password: Option[String],
      candidatesOnly: Boolean,
      maxPlayers: Option[Int],
      startDate: DateTime,
      berserkable: Boolean,
      streakable: Boolean,
      teamBattle: Option[TeamBattle],
      description: Option[String],
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
      position = sched.position,
      mode = Mode.Rated,
      conditions = sched.conditions,
      schedule = Some(sched),
      startsAt = sched.at,
    )

  def tournamentUrl(tourId: String): String = s"https://lishogi.org/tournament/$tourId"

  def makeId = ThreadLocalRandom nextString 8

  case class PastAndNext(past: List[Tournament], next: List[Tournament])
}
