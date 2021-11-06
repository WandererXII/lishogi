package lishogi.bot

import play.api.i18n.Lang
import play.api.libs.json._

import lishogi.common.Json.jodaWrites
import lishogi.game.JsonView._
import lishogi.game.{ Game, GameRepo, Pov }

final class BotJsonView(
    lightUserApi: lishogi.user.LightUserApi,
    gameRepo: GameRepo,
    rematches: lishogi.game.Rematches
)(implicit ec: scala.concurrent.ExecutionContext) {

  def gameFull(game: Game)(implicit lang: Lang): Fu[JsObject] = gameRepo.withInitialFen(game) flatMap gameFull

  def gameFull(wf: Game.WithInitialFen)(implicit lang: Lang): Fu[JsObject] =
    gameState(wf) map { state =>
      gameImmutable(wf) ++ Json.obj(
        "type"  -> "gameFull",
        "state" -> state
      )
    }

  def gameImmutable(wf: Game.WithInitialFen)(implicit lang: Lang): JsObject = {
    import wf._
    Json
      .obj(
        "id"      -> game.id,
        "variant" -> game.variant,
        "clock"   -> game.clock.map(_.config),
        "speed"   -> game.speed.key,
        "perf" -> game.perfType.map { p =>
          Json.obj("name" -> p.trans)
        },
        "rated"      -> game.rated,
        "createdAt"  -> game.createdAt,
        "sente"      -> playerJson(game.sentePov),
        "white"      -> playerJson(game.sentePov), // backwards support
        "gote"       -> playerJson(game.gotePov),
        "black"      -> playerJson(game.gotePov), // backwards support
        "initialFen" -> fen.fold("startpos")(_.value)
      )
      .add("tournamentId" -> game.tournamentId)
  }

  def gameState(wf: Game.WithInitialFen): Fu[JsObject] = {
    import wf._
    shogi.format.UciDump(game.pgnMoves, fen.map(_.value), game.variant).future map { uciMoves =>
      Json
        .obj(
          "type"   -> "gameState",
          "moves"  -> uciMoves.mkString(" "),
          "btime"  -> millisOf(game.sentePov),
          "wtime"  -> millisOf(game.gotePov),
          "binc"   -> game.clock.??(_.config.increment.millis),
          "winc"   -> game.clock.??(_.config.increment.millis),
          "byo"    -> game.clock.??(_.config.byoyomi.millis),
          "sdraw"  -> game.sentePlayer.isOfferingDraw,
          "gdraw"  -> game.gotePlayer.isOfferingDraw,
          "status" -> game.status.name
        )
        .add("winner" -> game.winnerColor)
        .add("rematch" -> rematches.of(game.id))
    }
  }

  def chatLine(username: String, text: String, player: Boolean) =
    Json.obj(
      "type"     -> "chatLine",
      "room"     -> (if (player) "player" else "spectator"),
      "username" -> username,
      "text"     -> text
    )

  private def playerJson(pov: Pov) = {
    val light = pov.player.userId flatMap lightUserApi.sync
    Json
      .obj()
      .add("aiLevel" -> pov.player.aiLevel)
      .add("id" -> light.map(_.id))
      .add("name" -> light.map(_.name))
      .add("title" -> light.map(_.title))
      .add("rating" -> pov.player.rating)
      .add("provisional" -> pov.player.provisional)
  }

  private def millisOf(pov: Pov): Int =
    pov.game.clock
      .map(_.remainingTime(pov.color).millis.toInt)
      .orElse(pov.game.correspondenceClock.map(_.remainingTime(pov.color).toInt * 1000))
      .getOrElse(Int.MaxValue)

  implicit private val clockConfigWriter: OWrites[shogi.Clock.Config] = OWrites { c =>
    Json.obj(
      "initial"   -> c.limit.millis,
      "increment" -> c.increment.millis,
      "byoyomi"   -> c.byoyomi.millis,
      "periods"   -> c.periods
    )
  }
}
