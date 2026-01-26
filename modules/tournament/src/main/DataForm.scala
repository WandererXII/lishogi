package lila.tournament

import scala.util.chaining._

import play.api.data.Forms._
import play.api.data._
import play.api.data.validation
import play.api.data.validation.Constraint
import play.api.data.validation.Constraints

import org.joda.time.DateTime

import shogi.Mode
import shogi.format.forsyth.Sfen

import lila.common.Form._
import lila.hub.LightTeam._
import lila.user.User

final class DataForm {

  import DataForm._

  def create(user: User, teamBattleId: Option[TeamID] = none) =
    form(user, none) fill TournamentSetup(
      name = teamBattleId.isEmpty option user.titleUsername,
      format = (if (teamBattleId.isDefined) Format.Arena.key else Format.Robin.key).some,
      timeControlSetup = TimeControl.DataForm.Setup.default,
      minutes = minutesDefault.some,
      startDate = none,
      finishDate = none,
      variant = shogi.variant.Standard.id.toString.some,
      position = none,
      rated = true.some,
      password = none,
      candidatesOnly = false.some,
      maxPlayers = none,
      conditions = Condition.DataForm.AllSetup.default,
      teamBattleByTeam = teamBattleId,
      berserkable = true.some,
      streakable = true.some,
      proMode = false.some,
      description = none,
      icon = none,
      hasChat = true.some,
    )

  def edit(user: User, tour: Tournament) =
    form(user, tour.some) fill TournamentSetup(
      name = tour.name.some,
      format = tour.format.key.some,
      timeControlSetup = TimeControl.DataForm.Setup(tour.timeControl),
      minutes = tour.isArena option tour.minutes,
      startDate = tour.startsAt.some,
      finishDate = tour.hasArrangements option tour.finishesAt,
      variant = tour.variant.id.toString.some,
      position = tour.position,
      rated = tour.mode.rated.some,
      password = tour.password,
      candidatesOnly = tour.candidatesOnly.some,
      maxPlayers = tour.maxPlayers,
      conditions = Condition.DataForm.AllSetup(tour.conditions),
      teamBattleByTeam = none,
      berserkable = tour.berserkable.some,
      streakable = tour.streakable.some,
      proMode = tour.proMode.some,
      description = tour.description,
      icon = tour.icon,
      hasChat = tour.hasChat.some,
    )

  private val nameType = cleanText.verifying(
    Constraints minLength 2,
    Constraints maxLength 30,
    Constraints.pattern(
      regex = """[\p{L}\p{N}-\s:,;]+""".r,
      error = "error.unknown",
    ),
    Constraint[String] { (t: String) =>
      if (t.toLowerCase contains "lishogi")
        validation.Invalid(validation.ValidationError("Must not contain \"lishogi\""))
      else validation.Valid
    },
  )

  private def form(user: User, prev: Option[Tournament]) =
    Form {
      makeMapping(user) pipe { m =>
        prev.fold(
          m.verifying(
            "Finish date needs to be in the future",
            _.finishDate.fold(true)(_.isAfter(DateTime.now)),
          ),
        ) { tour =>
          m
            .verifying(
              "Finish date needs to be in the future",
              tour.isFinished || _.finishDate.fold(true)(_.isAfter(DateTime.now)),
            )
            .verifying(
              "Can't change variant after players have joined",
              _.realVariant == tour.variant || tour.nbPlayers == 0,
            )
            .verifying(
              "Can't change clock control after players have joined",
              _.timeControlSetup.convert.key == tour.timeControl.key || tour.nbPlayers == 0,
            )
            .verifying(
              "Can't change rated mode after players have joined",
              _.realMode == tour.mode || tour.nbPlayers == 0,
            )
            .verifying(
              "Can't change to professional mode after players have joined",
              ~_.proMode == tour.proMode || tour.nbPlayers == 0,
            )
            .verifying(
              "Can't change format after tournament is created",
              _.realFormat == tour.format,
            )
            .verifying(
              "Can't change tournament from 'candidates only' to open, if candidates list is not empty",
              ~_.candidatesOnly == tour.candidatesOnly || !tour.candidatesOnly || tour.candidates.isEmpty,
            )
            .verifying(
              "Can't change tournament duration after tournament ends",
              !tour.isFinished || _.realMinutes == tour.minutes,
            )
        }
      }
    }

  private def makeMapping(user: User) =
    mapping(
      "name"             -> optional(nameType),
      "format"           -> optional(stringIn(Format.all.map(_.key).toSet)),
      "timeControlSetup" -> TimeControl.DataForm.setup,
      "minutes"          -> optional(number),
      "startDate"        -> optional(ISODateTimeOrTimestamp.isoDateTimeOrTimestamp),
      "finishDate"       -> optional(ISODateTimeOrTimestamp.isoDateTimeOrTimestamp),
      "variant"          -> optional(text.verifying(v => guessVariant(v).isDefined)),
      "position"         -> optional(lila.common.Form.sfen.clean),
      "rated"            -> optional(boolean),
      "password"         -> optional(nonEmptyText),
      "candidatesOnly"   -> optional(boolean),
      "maxPlayers"       -> optional(number),
      "conditions"       -> Condition.DataForm.all,
      "teamBattleByTeam" -> optional(nonEmptyText),
      "berserkable"      -> optional(boolean),
      "streakable"       -> optional(boolean),
      "proMode"          -> optional(boolean),
      "description"      -> optional(cleanNonEmptyText(maxLength = 3000)),
      "icon"             -> optional(cleanNonEmptyText(maxLength = 32)),
      "hasChat"          -> optional(boolean),
    )(TournamentSetup.apply)(TournamentSetup.unapply)
      .verifying("Invalid starting position", _.validPosition)
      .verifying(
        "Provide valid duration",
        _.validMinutes(lila.security.Granter(_.ManageTournament)(user)),
      )
      .verifying("End date needs to come at least 20 minutes after start date", _.validFinishDate)
      .verifying("Games with this time control cannot be rated", _.validRatedVariant)
      .verifying("Cannot have correspondence in arena format", _.validTimeControl)
      .verifying("Increase tournament duration, or decrease game clock", _.validSufficientDuration)
      .verifying("Reduce tournament duration", _.validNotExcessiveDuration)
      .verifying("Team battle supports only arena format", _.validTeamBattleFormat)
      .verifying("Team battle doesn't support candidates only option", _.validCandidates)
      .verifying(
        "Invalid max players - limit must be less than format default (faq) and greater than 1",
        _.validMaxPlayers,
      )
}

object DataForm {

  import shogi.variant._

  val minutes =
    (20 to 60 by 5) ++ (70 to 120 by 10) ++ (150 to 360 by 30) ++ (420 to 600 by 60) :+ 720
  val minutesDefault = 60

  val validVariants =
    List(Standard, Minishogi, Chushogi, Annanshogi, Kyotoshogi, Checkshogi, Dobutsu)

  def guessVariant(from: String): Option[Variant] =
    validVariants.find { v =>
      v.key == from || from.toIntOption.exists(v.id ==)
    }
}

private[tournament] case class TournamentSetup(
    name: Option[String],
    format: Option[String],
    timeControlSetup: TimeControl.DataForm.Setup,
    minutes: Option[Int],
    startDate: Option[DateTime],
    finishDate: Option[DateTime],
    variant: Option[String],
    position: Option[Sfen],
    rated: Option[Boolean],
    password: Option[String],
    candidatesOnly: Option[Boolean],
    maxPlayers: Option[Int],
    conditions: Condition.DataForm.AllSetup,
    teamBattleByTeam: Option[String],
    berserkable: Option[Boolean],
    streakable: Option[Boolean],
    proMode: Option[Boolean],
    description: Option[String],
    icon: Option[String],
    hasChat: Option[Boolean],
) {

  def realMode =
    if (position.filterNot(_.initialOf(realVariant)).isDefined) Mode.Casual
    else Mode(rated | true)

  def realVariant = variant.flatMap(DataForm.guessVariant) | shogi.variant.Standard

  def realFormat = format.flatMap(Format.byKey) | Format.Arena

  def realStartDate = startDate.getOrElse(DateTime.now)

  def realMinutes = finishDate
    .ifTrue(realFormat != Format.Arena)
    .map { fd =>
      ((fd.getMillis - realStartDate.getMillis) / 60000).toInt
    }
    .orElse(minutes)
    .getOrElse(DataForm.minutesDefault)

  def validPosition = position.fold(true) { sfen =>
    sfen.toSituation(realVariant).exists(_.playable(strict = true))
  }

  def validMinutes(granted: Boolean) =
    realFormat != Format.Arena || (~minutes.map(m => granted || minutes.contains(m)))

  def validFinishDate =
    realFormat == Format.Arena || finishDate.exists(_.minusMinutes(19) isAfter realStartDate)

  def validTimeControl = timeControlSetup.isRealTime || realFormat != Format.Arena

  def validRatedVariant =
    realMode == Mode.Casual ||
      lila.game.Game.allowRated(position, timeControlSetup.clock, realVariant)

  def validSufficientDuration =
    if (timeControlSetup.isRealTime)
      estimateNumberOfGamesOneCanPlay >= (if (realFormat == Format.Arena) 3 else 1)
    else realMinutes > 180

  def validNotExcessiveDuration =
    realMinutes <= minutesMax

  def validTeamBattleFormat = realFormat == Format.Arena || teamBattleByTeam.isEmpty

  def validCandidates = !(~candidatesOnly) || teamBattleByTeam.isEmpty

  def validMaxPlayers =
    maxPlayers.isEmpty || maxPlayers.exists(mp => mp <= Format.maxPlayers(realFormat) && mp > 1)

  def isPrivate = password.isDefined || conditions.teamMember.isDefined

  private def minutesMax =
    if (realFormat == Format.Arena) DataForm.minutes.last else 24 * 60 * 365 / 3

  private def estimateNumberOfGamesOneCanPlay: Double =
    (realMinutes * 60) / estimatedGameSeconds

  // There are 2 players, and they don't always use all their time (0.8)
  // add 15 seconds for pairing delay
  private def estimatedGameSeconds: Double = {
    (60 * timeControlSetup.clockTime + 30 * timeControlSetup.clockIncrement + timeControlSetup.clockByoyomi * 20 * timeControlSetup.periods) * 2 * 0.8
  } + 15

}
