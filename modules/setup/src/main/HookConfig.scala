package lila.setup

import shogi.Mode
import shogi.format.forsyth.Sfen

import lila.lobby.Color
import lila.lobby.Hook
import lila.lobby.Seek
import lila.rating.RatingRange
import lila.user.User

case class HookConfig(
    variant: shogi.variant.Variant,
    handicap: Option[Sfen],
    timeMode: TimeMode,
    time: Double,
    increment: Int,
    byoyomi: Int,
    periods: Int,
    days: Int,
    mode: Mode,
    color: Color,
    ratingRange: RatingRange,
) extends HumanConfig {

  def withinLimits(user: Option[User]): HookConfig =
    (
      user.map { me =>
        copy(
          ratingRange = ratingRange.withinLimits(
            rating = me.perfs(perfType).intRating,
            delta = 400,
            multipleOf = 50,
          ),
        )
      }
    ) | this

  private def perfType =
    lila.rating.PerfType.from(variant, hasClock = timeMode == TimeMode.RealTime)

  def >> =
    (
      variant.id,
      handicap.map(_.value),
      timeMode.id,
      time,
      increment,
      byoyomi,
      periods,
      days,
      mode.id.some,
      ratingRange.toString.some,
      color.name,
    ).some

  def withTimeModeString(tc: Option[String]) =
    tc match {
      case Some("realTime")       => copy(timeMode = TimeMode.RealTime)
      case Some("correspondence") => copy(timeMode = TimeMode.Correspondence)
      case Some("unlimited")      => copy(timeMode = TimeMode.Unlimited)
      case _                      => this
    }

  def hook(
      sri: lila.socket.Socket.Sri,
      user: Option[User],
      sid: Option[String],
      blocking: Set[String],
  ): Either[Hook, Option[Seek]] =
    timeMode match {
      case TimeMode.RealTime =>
        val clock = justMakeClock
        Left(
          Hook.make(
            sri = sri,
            variant = variant,
            handicap = handicap.map(_.value),
            clock = clock,
            mode = if (lila.game.Game.allowRated(None, clock.some, variant)) mode else Mode.Casual,
            color = color.name,
            user = user,
            blocking = blocking,
            sid = sid,
            ratingRange = ratingRange,
          ),
        )
      case _ =>
        Right(user map { u =>
          Seek.make(
            variant = variant,
            handicap = handicap.map(_.value),
            daysPerTurn = makeDaysPerTurn,
            mode = mode,
            color = color.name,
            user = u,
            blocking = blocking,
            ratingRange = ratingRange,
          )
        })
    }

  def noRatedUnlimited = mode.casual || hasClock || makeDaysPerTurn.isDefined

  def updateFrom(game: lila.game.Game) =
    copy(
      variant = game.variant,
      timeMode = TimeMode ofGame game,
      time = game.clock.map(_.limitInMinutes) | time,
      increment = game.clock.map(_.incrementSeconds) | increment,
      byoyomi = game.clock.map(_.byoyomiSeconds) | byoyomi,
      periods = game.clock.map(_.periodsTotal) | periods,
      days = game.daysPerTurn | days,
      mode = game.mode,
    )

  def withRatingRange(str: Option[String]) = copy(ratingRange = RatingRange orDefault str)
}

object HookConfig extends BaseHumanConfig {

  def from(
      v: Int,
      h: Option[String],
      tm: Int,
      t: Double,
      i: Int,
      b: Int,
      p: Int,
      d: Int,
      m: Option[Int],
      e: Option[String],
      c: String,
  ) = {
    val realMode = m.fold(Mode.default)(Mode.orDefault)
    val variant  = shogi.variant.Variant(v) err s"Invalid game variant $v"
    new HookConfig(
      variant = variant,
      handicap = h
        .map(Sfen.apply)
        .filter(sfen => sfen.toSituation(variant).exists(_.playable(strict = true))),
      timeMode = TimeMode(tm) err s"Invalid time mode $tm",
      time = t,
      increment = i,
      byoyomi = b,
      periods = p,
      days = d,
      mode = realMode,
      ratingRange = e.fold(RatingRange.default)(RatingRange.orDefault),
      color = Color(c) err s"Invalid color $c",
    )
  }

  val default = HookConfig(
    variant = variantDefault,
    handicap = none,
    timeMode = TimeMode.RealTime,
    time = 5d,
    increment = 0,
    byoyomi = 10,
    periods = 1,
    days = 2,
    mode = Mode.default,
    ratingRange = RatingRange.default,
    color = Color.default,
  )

  import lila.db.BSON
  import lila.db.dsl._

  implicit private[setup] val hookConfigBSONHandler: BSON[HookConfig] = new BSON[HookConfig] {

    def reads(r: BSON.Reader): HookConfig =
      HookConfig(
        variant = shogi.variant.Variant orDefault (r int "v"),
        handicap = r.strO("h").map(Sfen.apply),
        timeMode = TimeMode orDefault (r int "tm"),
        time = r double "t",
        increment = r int "i",
        byoyomi = r intD "b",
        periods = r intD "p",
        days = r int "d",
        mode = Mode orDefault (r int "m"),
        color = Color.Random,
        ratingRange = r strO "e" flatMap RatingRange.apply getOrElse RatingRange.default,
      )

    def writes(w: BSON.Writer, o: HookConfig) =
      $doc(
        "v"  -> o.variant.id,
        "h"  -> o.handicap.map(_.value),
        "tm" -> o.timeMode.id,
        "t"  -> o.time,
        "i"  -> o.increment,
        "b"  -> o.byoyomi,
        "p"  -> o.periods,
        "d"  -> o.days,
        "m"  -> o.mode.id,
        "e"  -> o.ratingRange.toString,
      )
  }
}
