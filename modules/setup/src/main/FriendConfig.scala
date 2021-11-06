package lishogi.setup

import shogi.Mode
import shogi.format.FEN
import lishogi.lobby.Color
import lishogi.rating.PerfType
import lishogi.game.PerfPicker

case class FriendConfig(
    variant: shogi.variant.Variant,
    timeMode: TimeMode,
    time: Double,
    increment: Int,
    byoyomi: Int,
    periods: Int,
    days: Int,
    mode: Mode,
    color: Color,
    fen: Option[FEN] = None
) extends HumanConfig
    with Positional {

  val strictFen = false

  def >> = (
    variant.id,
    timeMode.id,
    time,
    increment,
    byoyomi,
    periods,
    days,
    mode.id.some,
    color.name,
    fen.map(_.value)
  ).some

  def isPersistent = timeMode == TimeMode.Unlimited || timeMode == TimeMode.Correspondence

  def perfType: Option[PerfType] = PerfPicker.perfType(shogi.Speed(makeClock), variant, makeDaysPerTurn)
}

object FriendConfig extends BaseHumanConfig {

  def from(
      v: Int,
      tm: Int,
      t: Double,
      i: Int,
      b: Int,
      p: Int,
      d: Int,
      m: Option[Int],
      c: String,
      fen: Option[String]
  ) =
    new FriendConfig(
      variant = shogi.variant.Variant(v) err "Invalid game variant " + v,
      timeMode = TimeMode(tm) err s"Invalid time mode $tm",
      time = t,
      increment = i,
      byoyomi = b,
      periods = p,
      days = d,
      mode = m.fold(Mode.default)(Mode.orDefault),
      color = Color(c) err "Invalid color " + c,
      fen = fen map FEN
    )

  val default = FriendConfig(
    variant = variantDefault,
    timeMode = TimeMode.Unlimited,
    time = 5d,
    increment = 0,
    byoyomi = 10,
    periods = 1,
    days = 2,
    mode = Mode.default,
    color = Color.default
  )

  import lishogi.db.BSON
  import lishogi.db.dsl._
  import lishogi.game.BSONHandlers.FENBSONHandler

  implicit private[setup] val friendConfigBSONHandler = new BSON[FriendConfig] {

    def reads(r: BSON.Reader): FriendConfig =
      FriendConfig(
        variant = shogi.variant.Variant orDefault (r int "v"),
        timeMode = TimeMode orDefault (r int "tm"),
        time = r double "t",
        increment = r int "i",
        byoyomi = r intD "b",
        periods = r intD "p",
        days = r int "d",
        mode = Mode orDefault (r int "m"),
        color = Color.Sente,
        fen = r.getO[FEN]("f") filter (_.value.nonEmpty)
      )

    def writes(w: BSON.Writer, o: FriendConfig) =
      $doc(
        "v"  -> o.variant.id,
        "tm" -> o.timeMode.id,
        "t"  -> o.time,
        "i"  -> o.increment,
        "b"  -> o.byoyomi,
        "p"  -> o.periods,
        "d"  -> o.days,
        "m"  -> o.mode.id,
        "f"  -> o.fen
      )
  }
}
