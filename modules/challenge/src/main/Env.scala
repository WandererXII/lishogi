package lishogi.challenge

import com.softwaremill.macwire._
import play.api.Configuration
import scala.concurrent.duration._

import lishogi.common.config._
import lishogi.socket.Socket.{ GetVersion, SocketVersion }

@Module
final class Env(
    appConfig: Configuration,
    gameRepo: lishogi.game.GameRepo,
    userRepo: lishogi.user.UserRepo,
    onStart: lishogi.round.OnStart,
    gameCache: lishogi.game.Cached,
    lightUser: lishogi.common.LightUser.GetterSync,
    isOnline: lishogi.socket.IsOnline,
    db: lishogi.db.Db,
    cacheApi: lishogi.memo.CacheApi,
    prefApi: lishogi.pref.PrefApi,
    relationApi: lishogi.relation.RelationApi,
    remoteSocketApi: lishogi.socket.RemoteSocket,
    baseUrl: BaseUrl
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: akka.actor.ActorSystem,
    mode: play.api.Mode
) {

  private lazy val maxPlaying = appConfig.get[Max]("setup.max_playing")

  def version(challengeId: Challenge.ID): Fu[SocketVersion] =
    socket.rooms.ask[SocketVersion](challengeId)(GetVersion)

  private lazy val joiner = wire[Joiner]

  lazy val maker = wire[ChallengeMaker]

  lazy val api = wire[ChallengeApi]

  private lazy val socket = wire[ChallengeSocket]

  lazy val granter = wire[ChallengeGranter]

  private lazy val repo = new ChallengeRepo(
    coll = db(CollName("challenge")),
    maxPerUser = maxPlaying
  )

  lazy val jsonView = wire[JsonView]

  system.scheduler.scheduleWithFixedDelay(10 seconds, 3 seconds) { () =>
    api.sweep
  }
}
