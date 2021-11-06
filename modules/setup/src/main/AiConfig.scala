package lishogi.setup

import shogi.format.FEN
import lishogi.game.{ Game, Player, Pov, Source }
import lishogi.lobby.Color
import lishogi.user.User

case class AiConfig(
    variant: shogi.variant.Variant,
    timeMode: TimeMode,
    time: Double,
    increment: Int,
    byoyomi: Int,
    periods: Int,
    days: Int,
    level: Int,
    color: Color,
    fen: Option[FEN] = None
) extends Config
    with Positional {

  val strictFen = true

  def >> = (
    variant.id,
    timeMode.id,
    time,
    increment,
    byoyomi,
    periods,
    days,
    level,
    color.name,
    fen.map(_.value)
  ).some

  def game(user: Option[User]) = {
    fenGame { shogiGame =>
      val perfPicker = lishogi.game.PerfPicker.mainOrDefault(
        shogi.Speed(shogiGame.clock.map(_.config)),
        shogiGame.situation.board.variant,
        makeDaysPerTurn
      )
      Game
        .make(
          shogi = shogiGame,
          sentePlayer = creatorColor.fold(
            Player.make(shogi.Sente, user, perfPicker),
            Player.make(shogi.Sente, level.some)
          ),
          gotePlayer = creatorColor.fold(
            Player.make(shogi.Gote, level.some),
            Player.make(shogi.Gote, user, perfPicker)
          ),
          mode = shogi.Mode.Casual,
          source = if (shogiGame.board.variant.fromPosition) Source.Position else Source.Ai,
          daysPerTurn = makeDaysPerTurn,
          notationImport = None
        )
        .sloppy
    } start
  }

  def pov(user: Option[User]) = Pov(game(user), creatorColor)

  def timeControlFromPosition =
    variant != shogi.variant.FromPosition || time >= 1 || byoyomi >= 10 || increment >= 5
}

object AiConfig extends BaseConfig {

  def from(
      v: Int,
      tm: Int,
      t: Double,
      i: Int,
      b: Int,
      p: Int,
      d: Int,
      level: Int,
      c: String,
      fen: Option[String]
  ) =
    new AiConfig(
      variant = shogi.variant.Variant(v) err "Invalid game variant " + v,
      timeMode = TimeMode(tm) err s"Invalid time mode $tm",
      time = t,
      increment = i,
      byoyomi = b,
      periods = p,
      days = d,
      level = level,
      color = Color(c) err "Invalid color " + c,
      fen = fen map FEN
    )

  val default = AiConfig(
    variant = variantDefault,
    timeMode = TimeMode.Unlimited,
    time = 5d,
    increment = 0,
    byoyomi = 10,
    periods = 1,
    days = 2,
    level = 1,
    color = Color.default
  )

  val levels = (1 to 8).toList

  val levelChoices = levels map { l =>
    (l.toString, l.toString, none)
  }

  import lishogi.db.BSON
  import lishogi.db.dsl._
  import lishogi.game.BSONHandlers.FENBSONHandler

  implicit private[setup] val aiConfigBSONHandler = new BSON[AiConfig] {

    def reads(r: BSON.Reader): AiConfig =
      AiConfig(
        variant = shogi.variant.Variant orDefault (r int "v"),
        timeMode = TimeMode orDefault (r int "tm"),
        time = r double "t",
        increment = r int "i",
        byoyomi = r intD "b",
        periods = r intD "p",
        days = r int "d",
        level = r int "l",
        color = Color.Sente,
        fen = r.getO[FEN]("f") filter (_.value.nonEmpty)
      )

    def writes(w: BSON.Writer, o: AiConfig) =
      $doc(
        "v"  -> o.variant.id,
        "tm" -> o.timeMode.id,
        "t"  -> o.time,
        "i"  -> o.increment,
        "b"  -> o.byoyomi,
        "p"  -> o.periods,
        "d"  -> o.days,
        "l"  -> o.level,
        "f"  -> o.fen
      )
  }
}
