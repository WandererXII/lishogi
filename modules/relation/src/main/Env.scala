package lishogi.relation

import akka.actor._
import com.softwaremill.macwire._
import io.methvin.play.autoconfig._
import play.api.Configuration

import lishogi.common.config._
import lishogi.hub.actors

@Module
private class RelationConfig(
    @ConfigName("collection.relation") val collection: CollName,
    @ConfigName("limit.follow") val maxFollow: Max,
    @ConfigName("limit.block") val maxBlock: Max
)

@Module
final class Env(
    appConfig: Configuration,
    db: lishogi.db.Db,
    timeline: actors.Timeline,
    userRepo: lishogi.user.UserRepo,
    prefApi: lishogi.pref.PrefApi,
    cacheApi: lishogi.memo.CacheApi
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: ActorSystem
) {

  private val config = appConfig.get[RelationConfig]("relation")(AutoConfig.loader)

  def maxFollow = config.maxFollow

  private lazy val coll = db(config.collection)

  private lazy val repo: RelationRepo = wire[RelationRepo]

  lazy val api: RelationApi = wire[RelationApi]

  lazy val stream = wire[RelationStream]
}
