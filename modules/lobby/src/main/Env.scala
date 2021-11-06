package lishogi.lobby

import com.softwaremill.macwire._
import play.api.Configuration
import scala.concurrent.duration._

import lishogi.common.config._

@Module
final class Env(
    appConfig: Configuration,
    db: lishogi.db.Db,
    onStart: lishogi.round.OnStart,
    relationApi: lishogi.relation.RelationApi,
    playbanApi: lishogi.playban.PlaybanApi,
    gameCache: lishogi.game.Cached,
    userRepo: lishogi.user.UserRepo,
    gameRepo: lishogi.game.GameRepo,
    poolApi: lishogi.pool.PoolApi,
    cacheApi: lishogi.memo.CacheApi,
    remoteSocketApi: lishogi.socket.RemoteSocket
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: akka.actor.ActorSystem,
    idGenerator: lishogi.game.IdGenerator
) {

  private lazy val maxPlaying = appConfig.get[Max]("setup.max_playing")

  private lazy val seekApiConfig = new SeekApi.Config(
    coll = db(CollName("seek")),
    archiveColl = db(CollName("seek_archive")),
    maxPerPage = MaxPerPage(13),
    maxPerUser = Max(5)
  )

  lazy val seekApi = wire[SeekApi]

  lazy val boardApiHookStream = wire[BoardApiHookStream]

  private lazy val lobbyTrouper = LobbyTrouper.start(
    broomPeriod = 2 seconds,
    resyncIdsPeriod = 25 seconds
  ) { () =>
    wire[LobbyTrouper]
  }

  private lazy val abortListener = wire[AbortListener]

  private lazy val biter = wire[Biter]

  lazy val socket = wire[LobbySocket]

  lishogi.common.Bus.subscribeFun("abortGame") { case lishogi.game.actorApi.AbortedBy(pov) =>
    abortListener(pov)
  }
}
