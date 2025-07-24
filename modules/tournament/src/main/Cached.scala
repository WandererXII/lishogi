package lila.tournament

import scala.concurrent.duration._

import play.api.i18n.Lang
import play.api.libs.json.JsObject
import play.api.libs.json.Json

import lila.hub.LightTeam.TeamID
import lila.memo.CacheApi._
import lila.memo._
import lila.user.LightUserApi
import lila.user.User

final private[tournament] class Cached(
    playerRepo: PlayerRepo,
    pairingRepo: PairingRepo,
    arrangementRepo: ArrangementRepo,
    tournamentRepo: TournamentRepo,
    lightUserApi: LightUserApi,
    cacheApi: CacheApi,
)(implicit
    ec: scala.concurrent.ExecutionContext,
) {

  val nameCache = cacheApi.sync[(Tournament.ID, Lang), Option[String]](
    name = "tournament.name",
    initialCapacity = 512,
    compute = { case (id, lang) =>
      tournamentRepo byId id dmap2 { _.trans(lang) }
    },
    default = _ => none,
    strategy = Syncache.WaitAfterUptime(20 millis),
    expireAfter = Syncache.ExpireAfterAccess(25 minutes),
  )

  val arrangementNameCache = cacheApi.sync[Arrangement.ID, Option[String]](
    name = "tournament.arrangement.name",
    initialCapacity = 256,
    compute = { case id =>
      arrangementRepo nameById id
    },
    default = _ => none,
    strategy = Syncache.WaitAfterUptime(50 millis),
    expireAfter = Syncache.ExpireAfterAccess(20 minutes),
  )

  def ranking(tour: Tournament): Fu[Ranking] =
    if (tour.isFinished) finishedRanking get tour.id
    else ongoingRanking get tour.id

  private[tournament] val teamInfo =
    cacheApi[(Tournament.ID, TeamID), Option[TeamBattle.TeamInfo]](16, "tournament.teamInfo") {
      _.expireAfterWrite(5 seconds)
        .maximumSize(64)
        .buildAsyncFuture { case (tourId, teamId) =>
          playerRepo.teamInfo(tourId, teamId) dmap some
        }
    }

  // only applies to ongoing tournaments
  private val ongoingRanking = cacheApi[Tournament.ID, Ranking](64, "tournament.ongoingRanking") {
    _.expireAfterWrite(3 seconds)
      .buildAsyncFuture(playerRepo.computeRanking)
  }

  // only applies to finished tournaments
  private val finishedRanking =
    cacheApi[Tournament.ID, Ranking](1024, "tournament.finishedRanking") {
      _.expireAfterAccess(1 hour)
        .maximumSize(2048)
        .buildAsyncFuture(playerRepo.computeRanking)
    }

  object battle {

    val teamStanding =
      cacheApi[Tournament.ID, List[TeamBattle.RankedTeam]](32, "tournament.teamStanding") {
        _.expireAfterWrite(1 second)
          .buildAsyncFuture { id =>
            tournamentRepo teamBattleOf id flatMap {
              _ ?? { playerRepo.bestTeamIdsByTour(id, _) }
            }
          }
      }
  }

  private[tournament] object sheet {

    import arena.Sheet

    private case class SheetKey(
        tourId: Tournament.ID,
        userId: User.ID,
        version: Sheet.Version,
        streakable: Sheet.Streakable,
    )

    def apply(tour: Tournament, userId: User.ID): Fu[Sheet] =
      cache.get(keyOf(tour, userId))

    def update(tour: Tournament, userId: User.ID): Fu[Sheet] = {
      val key = keyOf(tour, userId)
      cache.invalidate(key)
      cache.get(key)
    }

    private def keyOf(tour: Tournament, userId: User.ID) =
      SheetKey(
        tour.id,
        userId,
        Sheet versionOf tour.startsAt,
        if (tour.streakable) Sheet.Streaks else Sheet.NoStreaks,
      )

    private def compute(key: SheetKey): Fu[Sheet] =
      pairingRepo.finishedByPlayerChronological(key.tourId, key.userId) map {
        arena.Sheet(key.userId, _, key.version, key.streakable)
      }

    private val cache = cacheApi[SheetKey, Sheet](2048, "tournament.sheet") {
      _.expireAfterAccess(3 minutes)
        .maximumSize(32768)
        .buildAsyncFuture(compute)
    }
  }

  private[tournament] object arrangement {

    private val playersCache =
      cacheApi[Tournament.ID, List[JsObject]](32, "tournament.arrangement.players") {
        _.expireAfterWrite(5 minutes)
          .buildAsyncFuture(computePlayers)
      }
    private val arrangementsCache =
      cacheApi[Tournament.ID, List[JsObject]](32, "tournament.arrangement.arrangements") {
        _.expireAfterWrite(3 minutes)
          .buildAsyncFuture(computeArrangements)
      }

    def apply(tourId: Tournament.ID) =
      for {
        players      <- playersCache get tourId
        arrangements <- arrangementsCache get tourId
      } yield Json.obj(
        "players"      -> players,
        "arrangements" -> arrangements,
      )

    def invalidatePlayers(tourId: Tournament.ID): Unit =
      playersCache.invalidate(tourId)

    def invalidateArrangements(tourId: Tournament.ID): Unit =
      arrangementsCache.invalidate(tourId)

    def computePlayers(tourId: Tournament.ID): Fu[List[JsObject]] =
      playerRepo
        .allByTour(tourId)
        .flatMap(
          _.sortBy(p => p.order.getOrElse(-p.magicScore))
            .map(JsonView.arrangementPlayerJson(lightUserApi, _))
            .sequenceFu,
        )

    def computeArrangements(tourId: Tournament.ID): Fu[List[JsObject]] =
      arrangementRepo.allByTour(tourId).map(_.map(JsonView.arrangement))

  }

  private[tournament] val notableFinishedCache = cacheApi.unit[List[Tournament]] {
    _.refreshAfterWrite(15 seconds)
      .buildAsyncFuture(_ => tournamentRepo.notableFinished(20))
  }
}
