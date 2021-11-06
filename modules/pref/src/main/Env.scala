package lishogi.pref

import com.softwaremill.macwire.Module

import lishogi.common.config.CollName

@Module
final class Env(
    cacheApi: lishogi.memo.CacheApi,
    db: lishogi.db.Db
)(implicit ec: scala.concurrent.ExecutionContext) {

  lazy val api = new PrefApi(db(CollName("pref")), cacheApi)
}
