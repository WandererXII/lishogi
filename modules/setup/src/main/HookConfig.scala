package lishogi.setup

import shogi.Mode
import lishogi.lobby.Color
import lishogi.lobby.{ Hook, Seek }
import lishogi.rating.RatingRange
import lishogi.user.User

case class HookConfig(
    variant: shogi.variant.Variant,
    timeMode: TimeMode,
    time: Double,
    increment: Int,
    byoyomi: Int,
    periods: Int,
    days: Int,
    mode: Mode,
    color: Color,
    ratingRange: RatingRange
) extends HumanConfig {

  def withinLimits(user: Option[User]): HookConfig =
    (for {
      pt <- perfType
      me <- user
    } yield copy(
      ratingRange = ratingRange.withinLimits(
        rating = me.perfs(pt).intRating,
        delta = 400,
        multipleOf = 50
      )
    )) | this

  private def perfType = lishogi.game.PerfPicker.perfType(makeSpeed, variant, makeDaysPerTurn)

  def makeSpeed = shogi.Speed(makeClock)

  def fixColor =
    copy(
      color =
        if (
          mode == Mode.Rated &&
          lishogi.game.Game.variantsWhereSenteIsBetter(variant) &&
          color != Color.Random
        ) Color.Random
        else color
    )

  def >> =
    (
      variant.id,
      timeMode.id,
      time,
      increment,
      byoyomi,
      periods,
      days,
      mode.id.some,
      ratingRange.toString.some,
      color.name
    ).some

  def withTimeModeString(tc: Option[String]) =
    tc match {
      case Some("realTime")       => copy(timeMode = TimeMode.RealTime)
      case Some("correspondence") => copy(timeMode = TimeMode.Correspondence)
      case Some("unlimited")      => copy(timeMode = TimeMode.Unlimited)
      case _                      => this
    }

  def hook(
      sri: lishogi.socket.Socket.Sri,
      user: Option[User],
      sid: Option[String],
      blocking: Set[String]
  ): Either[Hook, Option[Seek]] =
    timeMode match {
      case TimeMode.RealTime =>
        val clock = justMakeClock
        Left(
          Hook.make(
            sri = sri,
            variant = variant,
            clock = clock,
            mode = if (lishogi.game.Game.allowRated(variant, clock.some)) mode else Mode.Casual,
            color = color.name,
            user = user,
            blocking = blocking,
            sid = sid,
            ratingRange = ratingRange
          )
        )
      case _ =>
        Right(user map { u =>
          Seek.make(
            variant = variant,
            daysPerTurn = makeDaysPerTurn,
            mode = mode,
            color = color.name,
            user = u,
            blocking = blocking,
            ratingRange = ratingRange
          )
        })
    }

  def noRatedUnlimited = mode.casual || hasClock || makeDaysPerTurn.isDefined

  def updateFrom(game: lishogi.game.Game) =
    copy(
      variant = game.variant,
      timeMode = TimeMode ofGame game,
      time = game.clock.map(_.limitInMinutes) | time,
      increment = game.clock.map(_.incrementSeconds) | increment,
      byoyomi = game.clock.map(_.byoyomiSeconds) | byoyomi,
      periods = game.clock.map(_.periods) | periods,
      days = game.daysPerTurn | days,
      mode = game.mode
    )

  def withRatingRange(str: Option[String]) = copy(ratingRange = RatingRange orDefault str)
}

object HookConfig extends BaseHumanConfig {

  def from(
      v: Int,
      tm: Int,
      t: Double,
      i: Int,
      b: Int,
      p: Int,
      d: Int,
      m: Option[Int],
      e: Option[String],
      c: String
  ) = {
    val realMode = m.fold(Mode.default)(Mode.orDefault)
    new HookConfig(
      variant = shogi.variant.Variant(v) err s"Invalid game variant $v",
      timeMode = TimeMode(tm) err s"Invalid time mode $tm",
      time = t,
      increment = i,
      byoyomi = b,
      periods = p,
      days = d,
      mode = realMode,
      ratingRange = e.fold(RatingRange.default)(RatingRange.orDefault),
      color = Color(c) err s"Invalid color $c"
    )
  }

  val default = HookConfig(
    variant = variantDefault,
    timeMode = TimeMode.RealTime,
    time = 5d,
    increment = 0,
    byoyomi = 10,
    periods = 1,
    days = 2,
    mode = Mode.default,
    ratingRange = RatingRange.default,
    color = Color.default
  )

  import lishogi.db.BSON
  import lishogi.db.dsl._

  implicit private[setup] val hookConfigBSONHandler = new BSON[HookConfig] {

    def reads(r: BSON.Reader): HookConfig =
      HookConfig(
        variant = shogi.variant.Variant orDefault (r int "v"),
        timeMode = TimeMode orDefault (r int "tm"),
        time = r double "t",
        increment = r int "i",
        byoyomi = r intD "b",
        periods = r intD "p",
        days = r int "d",
        mode = Mode orDefault (r int "m"),
        color = Color.Random,
        ratingRange = r strO "e" flatMap RatingRange.apply getOrElse RatingRange.default
      )

    def writes(w: BSON.Writer, o: HookConfig) =
      $doc(
        "v"  -> o.variant.id,
        "tm" -> o.timeMode.id,
        "t"  -> o.time,
        "i"  -> o.increment,
        "b"  -> o.byoyomi,
        "p"  -> o.periods,
        "d"  -> o.days,
        "m"  -> o.mode.id,
        "e"  -> o.ratingRange.toString
      )
  }
}
