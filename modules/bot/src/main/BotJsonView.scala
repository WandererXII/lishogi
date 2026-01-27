package lila.bot

import play.api.i18n.Lang
import play.api.libs.json._

import lila.common.Json.jodaWrites
import lila.game.FairyConversion
import lila.game.Game
import lila.game.JsonView._
import lila.game.Pov

final class BotJsonView(
    lightUserApi: lila.user.LightUserApi,
    rematches: lila.game.Rematches,
) {

  def gameFull(game: Game)(implicit lang: Lang): JsObject =
    gameImmutable(game) ++ Json.obj(
      "type"  -> "gameFull",
      "state" -> gameState(game),
    )

  // Everything marked backwards will be removed at some point
  def gameImmutable(game: Game)(implicit lang: Lang): JsObject = {
    val sentePov    = playerJson(game.sentePov)
    val gotePov     = playerJson(game.gotePov)
    val initialSfen = game.initialSfen.fold("startpos")(_.value)
    Json
      .obj(
        "id"          -> game.id,
        "variant"     -> game.variant,
        "clock"       -> game.clock.map(_.config),
        "speed"       -> speedKeyBc(game), // backwards support
        "perf"        -> Json.obj("name" -> game.perfType.trans),
        "rated"       -> game.rated,
        "createdAt"   -> game.createdAt,
        "sente"       -> sentePov,
        "white"       -> sentePov,         // backwards support
        "gote"        -> gotePov,
        "black"       -> gotePov,          // backwards support
        "initialSfen" -> initialSfen,
        "initialFen"  -> initialSfen,      // backwards support
      )
      .add(
        "fairyInitialSfen" -> (FairyConversion.validVariant(game.variant) option
          game.initialSfen.fold("startpos") { sfen =>
            FairyConversion.makeFairySfen(game.variant, sfen).map(_.value) | sfen.value
          }),
      )
      .add("tournamentId" -> game.tournamentId)
      .add("arrangementId" -> game.arrangementId)
  }

  def gameState(game: Game): JsObject = {
    Json
      .obj(
        "type"   -> "gameState",
        "moves"  -> game.usis.map(_.usi).mkString(" "),
        "btime"  -> millisOf(game.sentePov),
        "wtime"  -> millisOf(game.gotePov),
        "binc"   -> game.clock.??(_.config.increment.millis),
        "winc"   -> game.clock.??(_.config.increment.millis),
        "byo"    -> game.clock.??(_.config.byoyomi.millis),
        "sdraw"  -> game.sentePlayer.isOfferingDraw,
        "gdraw"  -> game.gotePlayer.isOfferingDraw,
        "status" -> game.status.name,
      )
      .add(
        "fairyMoves" -> FairyConversion.makeFairyUsiList(game.variant, game.usis, game.initialSfen),
      )
      .add("winner" -> game.winnerColor)
      .add("rematch" -> rematches.getAcceptedId(game.id))
  }

  def chatLine(username: String, text: String, player: Boolean) =
    Json.obj(
      "type"     -> "chatLine",
      "room"     -> (if (player) "player" else "spectator"),
      "username" -> username,
      "text"     -> text,
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
      .map(_.currentClockFor(pov.color).time.millis.toInt)
      .orElse(pov.game.correspondenceClock.map(_.remainingTime(pov.color).toInt * 1000))
      .getOrElse(Int.MaxValue)

  private def speedKeyBc(g: Game): String =
    if (g.isCorrespondence) "correspondence"
    else if (g.isVeryVeryFast) "ultraBullet"
    else if (g.isVeryFast) "bullet"
    else if (g.isFast) "blitz"
    else if (g.isSlow) "rapid"
    else "classical"

  implicit private val clockConfigWriter: OWrites[shogi.Clock.Config] = OWrites { c =>
    Json.obj(
      "initial"   -> c.limit.millis,
      "increment" -> c.increment.millis,
      "byoyomi"   -> c.byoyomi.millis,
      "periods"   -> c.periodsTotal,
    )
  }
}
