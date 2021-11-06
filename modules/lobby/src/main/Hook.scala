package lishogi.lobby

import shogi.{ Clock, Mode, Speed }
import org.joda.time.DateTime
import ornicar.scalalib.Random
import play.api.i18n.Lang
import play.api.libs.json._

import lishogi.game.PerfPicker
import lishogi.rating.RatingRange
import lishogi.socket.Socket.Sri
import lishogi.user.User

// realtime shogi, volatile
case class Hook(
    id: String,
    sri: Sri,            // owner socket sri
    sid: Option[String], // owner cookie (used to prevent multiple hooks)
    variant: Int,
    clock: Clock.Config,
    mode: Int,
    color: String,
    user: Option[LobbyUser],
    ratingRange: String,
    createdAt: DateTime,
    boardApi: Boolean
) {

  val realColor = Color orDefault color

  val realVariant = shogi.variant.Variant orDefault variant

  val realMode = Mode orDefault mode

  val isAuth = user.nonEmpty

  def compatibleWith(h: Hook) =
    isAuth == h.isAuth &&
      mode == h.mode &&
      variant == h.variant &&
      clock == h.clock &&
      (realColor compatibleWith h.realColor) &&
      ratingRangeCompatibleWith(h) && h.ratingRangeCompatibleWith(this) &&
      (userId.isEmpty || userId != h.userId)

  private def ratingRangeCompatibleWith(h: Hook) =
    realRatingRange.fold(true) { range =>
      h.rating ?? range.contains
    }

  lazy val realRatingRange: Option[RatingRange] = isAuth ?? {
    RatingRange noneIfDefault ratingRange
  }

  def userId   = user.map(_.id)
  def username = user.fold(User.anonymous)(_.username)
  def lame     = user ?? (_.lame)

  lazy val perfType = PerfPicker.perfType(speed, realVariant, none)

  lazy val perf: Option[LobbyPerf] = for { u <- user; pt <- perfType } yield u perfAt pt
  def rating: Option[Int]          = perf.map(_.rating)

  def render(implicit lang: Lang): JsObject =
    Json
      .obj(
        "id"    -> id,
        "sri"   -> sri,
        "clock" -> clock.show,
        "t"     -> clock.estimateTotalSeconds,
        "s"     -> speed.id,
        "i"     -> (if (clock.incrementSeconds > 0) 1 else 0),
        "b"     -> (if (clock.byoyomiSeconds > 0) 1 else 0),
        "p"     -> (if (clock.periods > 1) 1 else 0)
      )
      .add("prov" -> perf.map(_.provisional).filter(identity))
      .add("u" -> user.map(_.username))
      .add("rating" -> rating)
      .add("variant" -> realVariant.exotic.option(realVariant.key))
      .add("ra" -> realMode.rated.option(1))
      .add("c" -> shogi.Color(color).map(_.name))
      .add("perf" -> perfType.map(_.trans))

  def randomColor = color == "random"

  lazy val compatibleWithPools =
    realMode.rated && realVariant.standard && randomColor &&
      lishogi.pool.PoolList.clockStringSet.contains(clock.show)

  def compatibleWithPool(poolClock: shogi.Clock.Config) =
    compatibleWithPools && clock == poolClock

  def toPool =
    lishogi.pool.HookThieve.PoolHook(
      hookId = id,
      member = lishogi.pool.PoolMember(
        userId = user.??(_.id),
        sri = sri,
        rating = rating | lishogi.rating.Glicko.defaultIntRating,
        ratingRange = realRatingRange,
        lame = user.??(_.lame),
        blocking = lishogi.pool.PoolMember.BlockedUsers(user.??(_.blocking)),
        since = createdAt,
        rageSitCounter = 0
      )
    )

  private lazy val speed = Speed(clock)
}

object Hook {

  val idSize = 8

  def make(
      sri: Sri,
      variant: shogi.variant.Variant,
      clock: Clock.Config,
      mode: Mode,
      color: String,
      user: Option[User],
      sid: Option[String],
      ratingRange: RatingRange,
      blocking: Set[String],
      boardApi: Boolean = false
  ): Hook =
    new Hook(
      id = lishogi.common.ThreadLocalRandom nextString idSize,
      sri = sri,
      variant = variant.id,
      clock = clock,
      mode = mode.id,
      color = color,
      user = user map { LobbyUser.make(_, blocking) },
      sid = sid,
      ratingRange = ratingRange.toString,
      createdAt = DateTime.now,
      boardApi = boardApi
    )
}
