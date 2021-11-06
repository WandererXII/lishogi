package lishogi.event

import play.api.Configuration
import com.softwaremill.macwire._

import lishogi.common.config.CollName
import lishogi.common.config._

final class Env(
    appConfig: Configuration,
    db: lishogi.db.Db,
    cacheApi: lishogi.memo.CacheApi
)(implicit ec: scala.concurrent.ExecutionContext) {

  private lazy val eventColl = db(appConfig.get[CollName]("event.collection.event"))

  lazy val api = wire[EventApi]
}
