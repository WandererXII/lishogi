package lila.challenge

import org.joda.time.DateTime

import shogi.Color
import shogi.Mode
import shogi.Speed
import shogi.format.forsyth.Sfen
import shogi.variant.Variant

import lila.game.PerfPicker
import lila.rating.PerfType
import lila.user.User

case class Challenge(
    _id: String,
    status: Challenge.Status,
    variant: Variant,
    initialSfen: Option[Sfen],
    timeControl: Challenge.TimeControl,
    mode: Mode,
    colorChoice: Challenge.ColorChoice,
    finalColor: shogi.Color,
    challenger: Challenge.Challenger,
    destUser: Option[Challenge.Challenger.Registered],
    rematchOf: Option[String],
    createdAt: DateTime,
    seenAt: Option[DateTime], // None for open challenges, so they don't sweep
    expiresAt: DateTime,
    open: Option[Boolean] = None,
) {

  import Challenge._

  def id = _id

  def challengerUser =
    challenger match {
      case u: Challenger.Registered => u.some
      case _                        => none
    }
  def challengerUserId = challengerUser.map(_.id)
  def challengerIsAnon =
    challenger match {
      case _: Challenger.Anonymous => true
      case _                       => false
    }
  def challengerIsOpen =
    challenger match {
      case Challenger.Open => true
      case _               => false
    }
  def destUserId = destUser.map(_.id)

  def userIds = List(challengerUserId, destUserId).flatten

  def daysPerTurn =
    timeControl match {
      case TimeControl.Correspondence(d) => d.some
      case _                             => none
    }
  def unlimited = timeControl == TimeControl.Unlimited

  def clock =
    timeControl match {
      case c: TimeControl.Clock => c.some
      case _                    => none
    }

  def hasClock = clock.isDefined

  def openDest = destUser.isEmpty
  def online   = status == Status.Created
  def active   = online || status == Status.Offline
  def declined = status == Status.Declined
  def accepted = status == Status.Accepted

  def setChallenger(u: Option[User], secret: Option[String]) =
    copy(
      challenger = u.map(toRegistered(variant, timeControl)) orElse
        secret.map(Challenger.Anonymous.apply) getOrElse Challenger.Open,
    )
  def setDestUser(u: User) =
    copy(
      destUser = toRegistered(variant, timeControl)(u).some,
    )

  def speed = speedOf(timeControl)

  def isOpen = ~open

  lazy val perfType = perfTypeOf(variant, timeControl)
}

object Challenge {

  type ID = String

  sealed abstract class Status(val id: Int) {
    val name = toString.toLowerCase
  }
  object Status {
    case object Created  extends Status(10)
    case object Offline  extends Status(15)
    case object Canceled extends Status(20)
    case object Declined extends Status(30)
    case object Accepted extends Status(40)
    val all                            = List(Created, Offline, Canceled, Declined, Accepted)
    def apply(id: Int): Option[Status] = all.find(_.id == id)
  }

  case class Rating(int: Int, provisional: Boolean) {
    def show = s"$int${if (provisional) "?" else ""}"
  }
  object Rating {
    def apply(p: lila.rating.Perf): Rating = Rating(p.intRating, p.provisional)
  }

  sealed trait Challenger
  object Challenger {
    case class Registered(id: User.ID, rating: Rating) extends Challenger
    case class Anonymous(secret: String)               extends Challenger
    case object Open                                   extends Challenger
  }

  sealed trait TimeControl
  object TimeControl {
    def make(clock: Option[shogi.Clock.Config], days: Option[Int]) =
      clock.map(Clock).orElse(days map Correspondence).getOrElse(Unlimited)
    case object Unlimited                extends TimeControl
    case class Correspondence(days: Int) extends TimeControl
    case class Clock(config: shogi.Clock.Config) extends TimeControl {
      // All durations are expressed in seconds
      def limit     = config.limit
      def increment = config.increment
      def byoyomi   = config.byoyomi
      def periods   = config.periodsTotal
      def show      = config.show
    }
  }

  sealed trait ColorChoice
  object ColorChoice {
    case object Random extends ColorChoice
    case object Sente  extends ColorChoice
    case object Gote   extends ColorChoice
    def apply(c: Color) = c.fold[ColorChoice](Sente, Gote)
  }

  private def speedOf(timeControl: TimeControl) =
    timeControl match {
      case TimeControl.Clock(config) => Speed(config)
      case _                         => Speed.Correspondence
    }

  private def perfTypeOf(variant: Variant, timeControl: TimeControl): PerfType =
    PerfPicker
      .perfType(
        speedOf(timeControl),
        variant,
        timeControl match {
          case TimeControl.Correspondence(d) => d.some
          case _                             => none
        },
      )
      .|(PerfType.Correspondence)

  private val idSize = 8

  private def randomId = lila.common.ThreadLocalRandom nextString idSize

  def toRegistered(variant: Variant, timeControl: TimeControl)(u: User) =
    Challenger.Registered(u.id, Rating(u.perfs(perfTypeOf(variant, timeControl))))

  def randomColor = shogi.Color.fromSente(lila.common.ThreadLocalRandom.nextBoolean())

  def make(
      variant: Variant,
      initialSfen: Option[Sfen],
      timeControl: TimeControl,
      mode: Mode,
      color: String,
      challenger: Challenger,
      destUser: Option[User],
      rematchOf: Option[String],
      isOpen: Boolean = false,
  ): Challenge = {
    val (colorChoice, finalColor) = color match {
      case "sente" => ColorChoice.Sente  -> shogi.Sente
      case "gote"  => ColorChoice.Gote   -> shogi.Gote
      case _       => ColorChoice.Random -> randomColor
    }
    val finalMode = timeControl match {
      case TimeControl.Clock(clock)
          if !lila.game.Game.allowRated(initialSfen, clock.some, variant) =>
        Mode.Casual
      case _ => mode
    }
    new Challenge(
      _id = randomId,
      status = Status.Created,
      variant = variant,
      initialSfen = initialSfen.filterNot(_.initialOf(variant)),
      timeControl = timeControl,
      mode = finalMode,
      colorChoice = colorChoice,
      finalColor = finalColor,
      challenger = challenger,
      destUser = destUser map toRegistered(variant, timeControl),
      rematchOf = rematchOf,
      createdAt = DateTime.now,
      seenAt = !isOpen option DateTime.now,
      expiresAt = if (isOpen) DateTime.now.plusDays(1) else inTwoWeeks,
      open = isOpen option true,
    )
  }
}
