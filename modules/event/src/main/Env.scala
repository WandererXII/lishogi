package lila.event

import com.softwaremill.macwire._

import lila.common.config._

final class Env(
    db: lila.db.Db,
    cacheApi: lila.memo.CacheApi,
)(implicit ec: scala.concurrent.ExecutionContext) {

  private lazy val eventColl = db(CollName("event"))

  lazy val api = wire[EventApi]
}
