package lila.user

import scala.concurrent.duration._

import reactivemongo.api.bson._

import lila.common.LightUser
import lila.db.dsl._
import lila.memo.CacheApi._
import lila.rating.Perf
import lila.rating.PerfType
import lila.user.User.LightCount
import lila.user.User.LightPerf

final class Cached(
    userRepo: UserRepo,
    onlineUserIds: () => Set[User.ID],
    mongoCache: lila.memo.MongoCache.Api,
    cacheApi: lila.memo.CacheApi,
    rankingApi: RankingApi,
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: akka.actor.ActorSystem,
) {

  implicit private val LightUserBSONHandler: BSONDocumentHandler[LightUser] =
    Macros.handler[LightUser]
  implicit private val LightPerfBSONHandler: BSONDocumentHandler[LightPerf] =
    Macros.handler[LightPerf]
  implicit private val LightCountBSONHandler: BSONDocumentHandler[LightCount] =
    Macros.handler[LightCount]

  val top10 = cacheApi.unit[Perfs.Leaderboards] {
    _.refreshAfterWrite(2 minutes)
      .buildAsyncFuture { _ =>
        rankingApi
          .fetchLeaderboard(10)
          .withTimeout(2 minutes)
          .monSuccess(_.user.leaderboardCompute)
      }
  }

  val top200Perf = mongoCache[Perf.ID, List[User.LightPerf]](
    PerfType.leaderboardable.size,
    "user:top200:perf",
    19 minutes,
    _.toString,
  ) { loader =>
    _.refreshAfterWrite(20 minutes)
      .buildAsyncFuture {
        loader {
          rankingApi.topPerf(_, 200)
        }
      }
  }

  private val topWeekCache = mongoCache.unit[List[User.LightPerf]](
    "user:top:week",
    9 minutes,
  ) { loader =>
    _.refreshAfterWrite(10 minutes)
      .buildAsyncFuture {
        loader { _ =>
          PerfType.leaderboardable
            .map { perf =>
              rankingApi.topPerf(perf.id, 2)
            }
            .sequenceFu
            .dmap(_.flatten)
        }
      }
  }

  def topWeek = topWeekCache.get {}

  val top10NbGame = mongoCache.unit[List[User.LightCount]](
    "user:top:nbGame",
    74 minutes,
  ) { loader =>
    _.refreshAfterWrite(75 minutes)
      .buildAsyncFuture {
        loader { _ =>
          userRepo topNbGame 10 dmap (_.map(_.lightCount))
        }
      }
  }

  private val top50OnlineCache = cacheApi.unit[List[User]] {
    _.refreshAfterWrite(1 minute)
      .buildAsyncFuture { _ =>
        userRepo.byIdsSortRatingNoBot(onlineUserIds(), 50)
      }
  }

  def getTop50Online = top50OnlineCache.getUnit

  def rankingsOf(userId: User.ID): Fu[Map[PerfType, Int]] = rankingApi.weeklyStableRanking of userId

  private[user] val botIds = cacheApi.unit[Set[User.ID]] {
    _.refreshAfterWrite(10 minutes)
      .buildAsyncFuture(_ => userRepo.botIds)
  }

  private def userIdsLikeFetch(text: String) =
    userRepo.userIdsLikeFilter(text, $empty, 12)

  private val userIdsLikeCache = cacheApi[String, List[User.ID]](64, "user.like") {
    _.expireAfterWrite(3 minutes).buildAsyncFuture(userIdsLikeFetch)
  }

  def userIdsLike(text: String): Fu[List[User.ID]] = {
    if (text.sizeIs < 5) userIdsLikeCache get text
    else userIdsLikeFetch(text)
  }
}
