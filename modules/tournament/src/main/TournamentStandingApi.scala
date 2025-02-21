package lila.tournament

import scala.concurrent.duration._

import play.api.libs.json._

import lila.common.WorkQueue
import lila.memo.CacheApi._

/*
 * Getting a standing page of a tournament can be very expensive
 * because it can iterate through thousands of mongodb documents.
 * Try to cache the stuff, and limit concurrent access to prevent
 * overloading mongodb.
 */
final class TournamentStandingApi(
    tournamentRepo: TournamentRepo,
    playerRepo: PlayerRepo,
    cached: Cached,
    cacheApi: lila.memo.CacheApi,
    lightUserApi: lila.user.LightUserApi,
)(implicit
    ec: scala.concurrent.ExecutionContext,
    mat: akka.stream.Materializer,
) {

  private val perPage = 10

  private val workQueue = new WorkQueue(
    buffer = 256,
    timeout = 5 seconds,
    name = "tournamentStandingApi",
    parallelism = 6,
  )

  def apply(tour: Tournament, forPage: Int): Fu[JsObject] = {
    val page = forPage atMost Math.ceil(tour.nbPlayers.toDouble / perPage).toInt atLeast 1
    if (page == 1) first get tour.id
    else if (page > 50) {
      if (tour.isCreated) createdCache.get(tour.id -> page)
      else computeMaybe(tour.id, page)
    } else compute(tour, page)
  }

  private val first = cacheApi[Tournament.ID, JsObject](16, "tournament.page.first") {
    _.expireAfterWrite(1 second)
      .buildAsyncFuture { compute(_, 1) }
  }

  private val createdCache =
    cacheApi[(Tournament.ID, Int), JsObject](4, "tournament.page.createdCache") {
      _.expireAfterWrite(15 second)
        .buildAsyncFuture { case (tourId, page) =>
          computeMaybe(tourId, page)
        }
    }

  def clearCache(tour: Tournament): Unit = {
    first invalidate tour.id
    // no need to invalidate createdCache, these are only cached when tour.isCreated
  }

  private def computeMaybe(id: Tournament.ID, page: Int): Fu[JsObject] =
    workQueue {
      compute(id, page)
    } recover { case _: Exception =>
      lila.mon.tournament.standingOverload.increment()
      Json.obj(
        "failed"  -> true,
        "page"    -> page,
        "players" -> JsArray(),
      )
    }

  private def compute(id: Tournament.ID, page: Int): Fu[JsObject] =
    tournamentRepo byId id orFail s"No such tournament: $id" flatMap { compute(_, page) }

  private def compute(tour: Tournament, page: Int): Fu[JsObject] =
    for {
      rankedPlayers <- playerRepo.bestByTourWithRankByPage(tour.id, perPage, page max 1)
      sheets <-
        rankedPlayers
          .map { p =>
            cached.sheet(tour, p.player.userId) dmap { p.player.userId -> _ }
          }
          .sequenceFu
          .dmap(_.toMap)
      players <- rankedPlayers
        .map(JsonView.arenaPlayerJson(lightUserApi, sheets, tour.streakable))
        .sequenceFu
    } yield Json.obj(
      "page"    -> page,
      "players" -> players,
    )
}
