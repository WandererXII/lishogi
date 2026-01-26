package lila.tournament
package crud

import play.api.data.Forms._
import play.api.data._

import org.joda.time.DateTime

import shogi.format.forsyth.Sfen
import shogi.variant.Variant

object CrudForm {

  import lila.common.Form.UTCDate._

  val maxHomepageHours = 24 * 7

  lazy val apply = Form(
    mapping(
      "name"             -> text(minLength = 3, maxLength = 40),
      "homepageHours"    -> number(min = 0, max = maxHomepageHours),
      "timeControlSetup" -> TimeControl.DataForm.setup,
      "minutes"          -> number(min = 20, max = 1440),
      "variant"          -> number.verifying(Variant exists _),
      "position"         -> optional(lila.common.Form.sfen.clean),
      "date"             -> utcDate,
      "icon"             -> optional(text(maxLength = 40)),
      "headline"         -> text(minLength = 5, maxLength = 30),
      "description"      -> text(minLength = 10, maxLength = 400),
      "conditions"       -> Condition.DataForm.all,
      "berserkable"      -> boolean,
      "teamBattle"       -> boolean,
    )(CrudForm.Data.apply)(CrudForm.Data.unapply)
      .verifying("Invalid clock", _.validClock)
      .verifying("Increase tournament duration, or decrease game clock", _.validTiming)
      .verifying("Custom position is not valid", _.isCustomPositionValid),
  ) fill CrudForm.Data(
    name = "",
    homepageHours = 0,
    timeControlSetup = TimeControl.DataForm.Setup.default,
    minutes = DataForm.minutesDefault,
    variant = shogi.variant.Standard.id,
    position = none,
    date = DateTime.now plusDays 7,
    icon = none,
    headline = "",
    description = "",
    conditions = Condition.DataForm.AllSetup.default,
    berserkable = true,
    teamBattle = false,
  )

  case class Data(
      name: String,
      homepageHours: Int,
      timeControlSetup: TimeControl.DataForm.Setup,
      minutes: Int,
      variant: Int,
      position: Option[Sfen],
      date: DateTime,
      icon: Option[String],
      headline: String,
      description: String,
      conditions: Condition.DataForm.AllSetup,
      berserkable: Boolean,
      teamBattle: Boolean,
  ) {

    def realVariant = Variant orDefault variant

    def validClock =
      if (timeControlSetup.isCorrespondence)
        timeControlSetup.daysPerTurn > 0
      else
        (timeControlSetup.clockTime + timeControlSetup.clockIncrement) > 0 || (timeControlSetup.clockTime + timeControlSetup.clockByoyomi) > 0

    def isCustomPositionValid =
      position.fold(true) { sfen =>
        sfen.toSituation(realVariant).exists(_.playable(strict = true))
      }

    def validTiming = (minutes * 60) >= (3 * estimatedGameDuration)

    private def estimatedGameDuration =
      60 * timeControlSetup.clockTime + 30 * timeControlSetup.clockIncrement + 20 * timeControlSetup.periods * timeControlSetup.clockByoyomi
  }

}
