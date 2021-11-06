package lishogi.simul

import akka.actor._
import com.softwaremill.macwire._
import io.methvin.play.autoconfig._
import play.api.Configuration
import scala.concurrent.duration._

import lishogi.common.Bus
import lishogi.common.config._
import lishogi.socket.Socket.{ GetVersion, SocketVersion }

@Module
private class SimulConfig(
    @ConfigName("collection.simul") val simulColl: CollName,
    @ConfigName("feature.views") val featureViews: Max
)

@Module
final class Env(
    appConfig: Configuration,
    db: lishogi.db.Db,
    gameRepo: lishogi.game.GameRepo,
    userRepo: lishogi.user.UserRepo,
    renderer: lishogi.hub.actors.Renderer,
    timeline: lishogi.hub.actors.Timeline,
    chatApi: lishogi.chat.ChatApi,
    lightUser: lishogi.common.LightUser.Getter,
    onGameStart: lishogi.round.OnStart,
    cacheApi: lishogi.memo.CacheApi,
    remoteSocketApi: lishogi.socket.RemoteSocket,
    proxyRepo: lishogi.round.GameProxyRepo
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: ActorSystem,
    mode: play.api.Mode
) {

  private val config = appConfig.get[SimulConfig]("simul")(AutoConfig.loader)

  private lazy val simulColl = db(config.simulColl)

  lazy val repo: SimulRepo = wire[SimulRepo]

  lazy val api: SimulApi = wire[SimulApi]

  lazy val jsonView = wire[JsonView]

  private val simulSocket = wire[SimulSocket]

  val isHosting = new lishogi.round.IsSimulHost(u => api.currentHostIds dmap (_ contains u))

  val allCreatedFeaturable = cacheApi.unit[List[Simul]] {
    _.refreshAfterWrite(3 seconds)
      .buildAsyncFuture(_ => repo.allCreatedFeaturable)
  }

  val featurable = new SimulIsFeaturable((simul: Simul) => featureLimiter(simul.hostId)(true)(false))

  private val featureLimiter = new lishogi.memo.RateLimit[lishogi.user.User.ID](
    credits = config.featureViews.value,
    duration = 24 hours,
    key = "simul.feature",
    log = false
  )

  def version(simulId: Simul.ID) =
    simulSocket.rooms.ask[SocketVersion](simulId)(GetVersion)

  Bus.subscribeFuns(
    "finishGame" -> { case lishogi.game.actorApi.FinishGame(game, _, _) =>
      api finishGame game
    },
    "adjustCheater" -> { case lishogi.hub.actorApi.mod.MarkCheater(userId, true) =>
      api ejectCheater userId
    },
    "simulGetHosts" -> { case lishogi.hub.actorApi.simul.GetHostIds(promise) =>
      promise completeWith api.currentHostIds
    },
    "moveEventSimul" -> { case lishogi.hub.actorApi.round.SimulMoveEvent(move, _, opponentUserId) =>
      Bus.publish(
        lishogi.hub.actorApi.socket.SendTo(
          opponentUserId,
          lishogi.socket.Socket.makeMessage("simulPlayerMove", move.gameId)
        ),
        "socketUsers"
      )
    }
  )
}

final class SimulIsFeaturable(f: Simul => Boolean) extends (Simul => Boolean) {
  def apply(simul: Simul) = f(simul)
}
