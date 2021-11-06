package lishogi.history

import com.softwaremill.macwire._

import lishogi.common.config.CollName

@Module
final class Env(
    mongoCache: lishogi.memo.MongoCache.Api,
    userRepo: lishogi.user.UserRepo,
    cacheApi: lishogi.memo.CacheApi,
    db: lishogi.db.Db
)(implicit ec: scala.concurrent.ExecutionContext) {

  private lazy val coll = db(CollName("history3"))

  lazy val api = wire[HistoryApi]

  lazy val ratingChartApi = wire[RatingChartApi]
}
