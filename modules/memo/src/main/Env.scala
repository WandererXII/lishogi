package lila.memo

import com.softwaremill.macwire._

import lila.common.config._

@Module
final class Env(
    mode: play.api.Mode,
    db: lila.db.Db,
)(implicit ec: scala.concurrent.ExecutionContext, system: akka.actor.ActorSystem) {

  lazy val settingStore = new SettingStore.Builder(db(CollName("flag")))

  lazy val cacheApi: CacheApi = wire[CacheApi]

  lazy val mongoCacheApi = new MongoCache.Api(db(CollName("cache")), cacheApi)
}
