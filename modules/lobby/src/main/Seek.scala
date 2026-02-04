package lila.lobby

import play.api.libs.json._

import org.joda.time.DateTime

import shogi.Mode

import lila.rating.RatingRange
import lila.user.User

// correspondence shogi, persistent
case class Seek(
    _id: String,
    variant: Int,
    handicap: Option[String],
    daysPerTurn: Option[Int],
    mode: Int,
    color: String,
    user: LobbyUser,
    ratingRange: String,
    createdAt: DateTime,
) {

  def id = _id

  val realColor = Color orDefault color

  val realVariant = shogi.variant.Variant orDefault variant

  val realMode = Mode orDefault mode

  def compatibleWith(h: Seek) =
    user.id != h.user.id &&
      compatibilityProperties == h.compatibilityProperties &&
      (realColor compatibleWith h.realColor) &&
      ratingRangeCompatibleWith(h) && h.ratingRangeCompatibleWith(this)

  private def ratingRangeCompatibleWith(s: Seek) =
    realRatingRange.fold(true) { range =>
      range.contains(s.rating)
    }

  private def compatibilityProperties = (variant, mode, daysPerTurn)

  lazy val realRatingRange: Option[RatingRange] = RatingRange noneIfDefault ratingRange

  def perf = user.perfAt(perfType)

  def rating = perf.rating

  def render: JsObject =
    Json
      .obj(
        "id"       -> _id,
        "username" -> user.username,
        "rating"   -> rating,
        "mode"     -> realMode.id,
        "perf"     -> perfType.key,
      )
      .add(
        "color",
        shogi.Color.fromName(color).map(_.name),
      )
      .add("days", daysPerTurn)
      .add("rr" -> (ratingRange != RatingRange.default.toString).option(ratingRange))
      .add("variant" -> (!realVariant.standard).option(realVariant.key))
      .add("handicap" -> handicap)
      .add("provisional" -> perf.provisional)
      .add("cc" -> user.countryCode)

  lazy val perfType = lila.rating.PerfType.from(realVariant, hasClock = false)
}

object Seek {

  val idSize = 8

  def make(
      variant: shogi.variant.Variant,
      handicap: Option[String],
      daysPerTurn: Option[Int],
      mode: Mode,
      color: String,
      user: User,
      ratingRange: RatingRange,
      blocking: Set[String],
  ): Seek =
    new Seek(
      _id = lila.common.ThreadLocalRandom nextString idSize,
      variant = variant.id,
      handicap = handicap,
      daysPerTurn = daysPerTurn,
      mode = mode.id,
      color = color,
      user = LobbyUser.make(user, blocking),
      ratingRange = ratingRange.toString,
      createdAt = DateTime.now,
    )

  def renew(seek: Seek) =
    new Seek(
      _id = lila.common.ThreadLocalRandom nextString idSize,
      variant = seek.variant,
      handicap = seek.handicap,
      daysPerTurn = seek.daysPerTurn,
      mode = seek.mode,
      color = seek.color,
      user = seek.user,
      ratingRange = seek.ratingRange,
      createdAt = DateTime.now,
    )

  import reactivemongo.api.bson._

  import lila.db.BSON.BSONJodaDateTimeHandler
  implicit val lobbyPerfBSONHandler: BSONHandler[LobbyPerf] =
    BSONIntegerHandler.as[LobbyPerf](
      b => LobbyPerf(b.abs, b < 0),
      x => x.rating * (if (x.provisional) -1 else 1),
    )
  implicit private[lobby] val lobbyUserBSONHandler: BSONDocumentHandler[LobbyUser] =
    Macros.handler[LobbyUser]
  implicit private[lobby] val seekBSONHandler: BSONDocumentHandler[Seek] = Macros.handler[Seek]
}
