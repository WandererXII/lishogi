package lila.setup

import play.api.data.Forms._
import play.api.data._

import shogi.format.forsyth.Sfen
import shogi.variant.Standard
import shogi.variant.Variant

import lila.rating.RatingRange
import lila.user.User
import lila.user.UserContext

final class FormFactory {

  import Mappings._

  val filter = Form(single("local" -> text))

  def aiFilled(sfen: Option[Sfen], variant: Option[Variant])(implicit
      ctx: UserContext,
  ): Form[AiConfig] =
    ai(ctx) fill sfen.foldLeft(AiConfig.default) { case (config, f) =>
      config.copy(sfen = f.some, variant = variant.getOrElse(Standard))
    }

  def ai(ctx: UserContext) = Form(
    mapping(
      "variant"   -> aiVariants,
      "timeMode"  -> timeMode,
      "time"      -> time,
      "increment" -> increment,
      "byoyomi"   -> byoyomi,
      "periods"   -> periods,
      "days"      -> days,
      "level"     -> level,
      "color"     -> color,
      "sfen"      -> sfenField,
    )(AiConfig.from)(_.>>)
      .verifying("invalidSfen", _.validSfen)
      .verifying("Invalid timemode", _.validTimeMode(ctx.isAuth))
      .verifying("Can't play that time control with this variant", _.timeControlNonStandard)
      .verifying(
        "Bots can't challenge the highest difficulty",
        _.validLevel(ctx.me.exists(_.isBot)),
      ),
  )

  def friendFilled(sfen: Option[Sfen], variant: Option[Variant])(implicit
      ctx: UserContext,
  ): Form[FriendConfig] =
    friend(ctx) fill sfen.foldLeft(FriendConfig.default) { case (config, f) =>
      config.copy(sfen = f.some, variant = variant.getOrElse(Standard))
    }

  def friend(ctx: UserContext) =
    Form(
      mapping(
        "variant"   -> variants,
        "timeMode"  -> timeMode,
        "time"      -> time,
        "increment" -> increment,
        "byoyomi"   -> byoyomi,
        "periods"   -> periods,
        "days"      -> days,
        "mode"      -> mode(withRated = ctx.isAuth),
        "color"     -> color,
        "sfen"      -> sfenField,
        "proMode"   -> optional(boolean),
      )(FriendConfig.from)(_.>>)
        .verifying("Invalid clock", _.validClock)
        .verifying("Invalid timemode", _.validTimeMode(ctx.isAuth))
        .verifying("Invalid speed", _.validSpeed(ctx.me.exists(_.isBot)))
        .verifying("invalidSfen", _.validSfen),
    )

  def hookFilled(timeModeString: Option[String])(implicit ctx: UserContext): Form[HookConfig] =
    hook fill HookConfig.default.withTimeModeString(timeModeString)

  def hook(implicit ctx: UserContext) = {
    Form(
      mapping(
        "variant"     -> variants,
        "timeMode"    -> timeMode,
        "time"        -> time,
        "increment"   -> increment,
        "byoyomi"     -> byoyomi,
        "periods"     -> periods,
        "days"        -> days,
        "mode"        -> mode(ctx.isAuth),
        "ratingRange" -> optional(ratingRange),
        "color"       -> color,
      )(HookConfig.from)(_.>>)
        .verifying("Invalid clock", _.validClock)
        .verifying("Invalid timemode", _.validTimeMode(ctx.isAuth))
        .verifying("Can't create rated unlimited in lobby", _.noRatedUnlimited),
    )
  }

  lazy val boardApiHook = Form(
    mapping(
      "time"        -> time,
      "increment"   -> increment,
      "byoyomi"     -> byoyomi,
      "periods"     -> periods,
      "variant"     -> optional(boardApiVariantKeys),
      "rated"       -> optional(boolean),
      "color"       -> optional(color),
      "ratingRange" -> optional(ratingRange),
    )((t, i, b, p, v, r, c, g) =>
      HookConfig(
        variant = v.flatMap(Variant.apply) | Variant.default,
        timeMode = TimeMode.RealTime,
        time = t,
        increment = i,
        byoyomi = b,
        periods = p,
        days = 1,
        mode = shogi.Mode(~r),
        color = lila.lobby.Color.orDefault(c),
        ratingRange = g.fold(RatingRange.default)(RatingRange.orDefault),
      ),
    )(_ => none)
      .verifying("Invalid clock", _.validClock)
      .verifying(
        "Invalid time control",
        hook =>
          hook.makeClock ?? {
            lila.game.Game.isBoardCompatible(_, hook.mode)
          },
      ),
  )

  object api {

    private lazy val clock = "clock" -> optional(
      mapping(
        "limit"     -> number.verifying(ApiConfig.clockLimitSeconds.contains _),
        "increment" -> increment,
        "byoyomi"   -> byoyomi,
        "periods"   -> periods,
      )(shogi.Clock.Config.apply)(shogi.Clock.Config.unapply),
    )

    private lazy val variant =
      "variant" -> optional(text.verifying(Variant.byKey.contains _))

    def user(from: User) = Form(
      mapping(
        variant,
        clock,
        "days"          -> optional(days),
        "rated"         -> boolean,
        "color"         -> optional(color),
        "sfen"          -> sfenField,
        "proMode"       -> optional(boolean),
        "acceptByToken" -> optional(nonEmptyText),
      )(ApiConfig.from)(_.>>)
        .verifying("invalidSfen", _.validSfen)
        .verifying("Invalid speed", _ validSpeed from.isBot),
    )

    lazy val ai = Form(
      mapping(
        "level" -> level,
        variant,
        clock,
        "days"  -> optional(days),
        "color" -> optional(color),
        "sfen"  -> sfenField,
      )(ApiAiConfig.from)(_.>>).verifying("invalidSfen", _.validSfen),
    )

    lazy val open = Form(
      mapping(
        variant,
        clock,
        "days"    -> optional(days),
        "rated"   -> boolean,
        "sfen"    -> sfenField,
        "proMode" -> optional(boolean),
      )(OpenConfig.from)(_.>>)
        .verifying("invalidSfen", _.validSfen)
        .verifying("rated without a clock", c => c.clock.isDefined || c.days.isDefined || !c.rated),
    )
  }
}
