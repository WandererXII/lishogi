package lila.api

import play.api.libs.json.JsArray
import play.api.libs.json.JsObject
import play.api.libs.json.Json

import lila.game.Pov
import lila.lobby.SeekApi

final class LobbyApi(
    lightUserApi: lila.user.LightUserApi,
    seekApi: SeekApi,
    gameProxyRepo: lila.round.GameProxyRepo,
    gameJson: lila.game.JsonView,
)(implicit ec: scala.concurrent.ExecutionContext) {

  def apply(implicit ctx: Context): Fu[(JsObject, List[Pov])] =
    ctx.me.fold(seekApi.forAnon)(seekApi.forUser).mon(_.lobby segment "seeks") zip
      (ctx.me ?? gameProxyRepo.urgentGames).mon(_.lobby segment "urgentGames") flatMap {
        case (seeks, povs) =>
          val displayedPovs = povs take 9
          lightUserApi.preloadMany(displayedPovs.flatMap(_.opponent.userId)) inject {
            Json.obj(
              "me" -> ctx.me.map { u =>
                Json
                  .obj(
                    "username" -> u.username,
                    "ratings" -> JsObject(u.perfs.perfsMap map { case (key, perf) =>
                      key -> Json
                        .obj(
                          "rating" -> perf.intRating,
                        )
                        .add("clueless" -> perf.clueless)
                    }),
                  )
                  .add("isBot" -> u.isBot)
                  .add("aiLevel" -> u.perfs.aiLevels.standard)
                  .add("isNewPlayer" -> !u.hasGames)
              },
              "seeks"        -> JsArray(seeks.map(_.render)),
              "nowPlaying"   -> JsArray(displayedPovs map nowPlaying),
              "nbNowPlaying" -> povs.size,
            ) -> displayedPovs
          }
      }

  def nowPlaying(pov: Pov) = gameJson.ownerPreview(pov)(lightUserApi.sync)
}
