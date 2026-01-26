package lila.simul

import play.api.data.Forms._
import play.api.data._
import play.api.data.validation.Constraint
import play.api.data.validation.Constraints

import org.joda.time.DateTime

import shogi.format.forsyth.Sfen

import lila.common.Form._
import lila.user.User

object SimulForm {

  // minutes
  val clockTimes: List[Int] = ((5 to 15 by 5) ++ (20 to 90 by 10) ++ (120 to 180 by 20)).toList
  val clockTimeDefault      = 20

  val clockIncrements: List[Int] =
    ((0 to 2 by 1) ++ (3 to 7) ++ (10 to 30 by 5) ++ (40 to 60 by 10) ++ (90 to 180 by 30)).toList
  val clockIncrementDefault = 0

  // minutes
  val clockExtras: List[Int] = ((0 to 15 by 5) ++ (20 to 60 by 10) ++ (90 to 120 by 30)).toList
  val clockExtraDefault      = 0

  val clockByoyomi: List[Int] =
    ((0 to 2 by 1) ++ (3 to 7) ++ (10 to 30 by 5) ++ (40 to 60 by 10) ++ (90 to 180 by 30)).toList
  val clockByoyomiDefault = 0

  val periods: List[Int] = (1 to 5).toList
  val periodsDefault     = 1

  val colors       = List("sente", "random", "gote")
  val colorDefault = "gote"

  private def nameType =
    cleanText.verifying(
      Constraints minLength 2,
      Constraints maxLength 40,
      Constraints.pattern(
        regex = """[\p{L}\p{N}-\s:,;]+""".r,
        error = "Invalid characters",
      ),
      Constraint[String] { (t: String) =>
        if (t.toLowerCase contains "lishogi")
          validation.Invalid(validation.ValidationError("Must not contain \"lishogi\""))
        else validation.Valid
      },
    )

  def create(host: User) =
    baseForm fill Setup(
      name = host.titleUsername,
      clockTime = clockTimeDefault,
      clockIncrement = clockIncrementDefault,
      clockByoyomi = clockByoyomiDefault,
      periods = periodsDefault,
      clockExtra = clockExtraDefault,
      variants = List(shogi.variant.Standard.id),
      position = none,
      color = colorDefault,
      proMode = false,
      text = "",
      estimatedStartAt = none,
      team = none,
    )
  def edit(simul: Simul) =
    baseForm fill Setup(
      name = simul.name,
      clockTime = simul.clock.config.limitInMinutes.toInt,
      clockIncrement = simul.clock.config.increment.roundSeconds,
      clockByoyomi = simul.clock.config.byoyomi.roundSeconds,
      periods = simul.clock.config.periodsTotal,
      clockExtra = simul.clock.hostExtraMinutes,
      variants = simul.variants.map(_.id),
      position = simul.position,
      color = simul.color | "random",
      proMode = ~simul.proMode,
      text = simul.text,
      estimatedStartAt = simul.estimatedStartAt,
      team = simul.team,
    )

  private def baseForm =
    Form(
      mapping(
        "name"           -> nameType,
        "clockTime"      -> numberIn(clockTimes),
        "clockIncrement" -> numberIn(clockIncrements),
        "clockByoyomi"   -> numberIn(clockByoyomi),
        "periods"        -> numberIn(periods),
        "clockExtra"     -> numberIn(clockExtras),
        "variants" -> list {
          number.verifying(
            Set(
              shogi.variant.Standard.id,
              shogi.variant.Minishogi.id,
              shogi.variant.Chushogi.id,
              shogi.variant.Annanshogi.id,
              shogi.variant.Kyotoshogi.id,
              shogi.variant.Checkshogi.id,
            ) contains _,
          )
        }.verifying("At least one variant", _.nonEmpty),
        "position"         -> optional(lila.common.Form.sfen.clean),
        "color"            -> stringIn(colors.toSet),
        "proMode"          -> boolean,
        "text"             -> cleanText(maxLength = 3000),
        "estimatedStartAt" -> optional(inTheFuture(ISODateTimeOrTimestamp.isoDateTimeOrTimestamp)),
        "team"             -> optional(nonEmptyText),
      )(Setup.apply)(Setup.unapply)
        .verifying("Custom position allowed only with one variant", _.canHaveCustomPosition)
        .verifying("Custom position is not valid", _.isCustomPositionValid),
    )

  def setText = Form(single("text" -> text))

  case class Setup(
      name: String,
      clockTime: Int,
      clockIncrement: Int,
      clockByoyomi: Int,
      periods: Int,
      clockExtra: Int,
      variants: List[Int],
      position: Option[Sfen],
      color: String,
      proMode: Boolean,
      text: String,
      estimatedStartAt: Option[DateTime] = None,
      team: Option[String],
  ) {
    def clock =
      SimulClock(
        config = shogi.Clock.Config(clockTime * 60, clockIncrement, clockByoyomi, periods),
        hostExtraTime = clockExtra * 60,
      )

    def canHaveCustomPosition =
      actualVariants.sizeIs == 1 || !position.isDefined

    def isCustomPositionValid =
      position.fold(true) { sfen =>
        sfen
          .toSituation(actualVariants.headOption | shogi.variant.Standard)
          .exists(_.playable(strict = true))
      }

    def actualVariants = variants.flatMap(shogi.variant.Variant(_)).distinct

  }
}
