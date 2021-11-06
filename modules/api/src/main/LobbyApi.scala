package lishogi.api

import play.api.libs.json.{ JsArray, JsObject, Json }

import lishogi.game.Pov
import lishogi.lobby.SeekApi

final class LobbyApi(
    lightUserApi: lishogi.user.LightUserApi,
    seekApi: SeekApi,
    gameProxyRepo: lishogi.round.GameProxyRepo
)(implicit ec: scala.concurrent.ExecutionContext) {

  def apply(implicit ctx: Context): Fu[(JsObject, List[Pov])] =
    ctx.me.fold(seekApi.forAnon)(seekApi.forUser).mon(_.lobby segment "seeks") zip
      (ctx.me ?? gameProxyRepo.urgentGames).mon(_.lobby segment "urgentGames") flatMap { case (seeks, povs) =>
        val displayedPovs = povs take 9
        lightUserApi.preloadMany(displayedPovs.flatMap(_.opponent.userId)) inject {
          implicit val lang = ctx.lang
          Json.obj(
            "me" -> ctx.me.map { u =>
              Json.obj("username" -> u.username).add("isBot" -> u.isBot)
            },
            "seeks"        -> JsArray(seeks map (_.render)),
            "nowPlaying"   -> JsArray(displayedPovs map nowPlaying),
            "nbNowPlaying" -> povs.size
          ) -> displayedPovs
        }
      }

  def nowPlaying(pov: Pov) =
    Json
      .obj(
        "fullId"   -> pov.fullId,
        "gameId"   -> pov.gameId,
        "fen"      -> (shogi.format.Forsyth exportSituation pov.game.situation),
        "color"    -> pov.color.name,
        "lastMove" -> ~pov.game.lastMoveKeys,
        "variant" -> Json.obj(
          "key"  -> pov.game.variant.key,
          "name" -> pov.game.variant.name
        ),
        "speed"    -> pov.game.speed.key,
        "perf"     -> lishogi.game.PerfPicker.key(pov.game),
        "rated"    -> pov.game.rated,
        "hasMoved" -> pov.hasMoved,
        "opponent" -> Json
          .obj(
            "id" -> pov.opponent.userId,
            "username" -> lishogi.game.Namer
              .playerTextBlocking(pov.opponent, withRating = false)(lightUserApi.sync)
          )
          .add("rating" -> pov.opponent.rating)
          .add("ai" -> pov.opponent.aiLevel),
        "isMyTurn" -> pov.isMyTurn
      )
      .add("secondsLeft" -> pov.remainingSeconds)
}
