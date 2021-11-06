package lishogi.swiss

import com.softwaremill.macwire._
import play.api.Configuration
import scala.concurrent.duration._

import lishogi.common.config._
import lishogi.common.{ AtMost, Every, ResilientScheduler }
import lishogi.socket.Socket.{ GetVersion, SocketVersion }

@Module
final class Env(
    appConfig: Configuration,
    db: lishogi.db.Db,
    gameRepo: lishogi.game.GameRepo,
    userRepo: lishogi.user.UserRepo,
    onStart: lishogi.round.OnStart,
    remoteSocketApi: lishogi.socket.RemoteSocket,
    chatApi: lishogi.chat.ChatApi,
    cacheApi: lishogi.memo.CacheApi,
    lightUserApi: lishogi.user.LightUserApi,
    gameProxyRepo: lishogi.round.GameProxyRepo,
    roundSocket: lishogi.round.RoundSocket,
    mongoCache: lishogi.memo.MongoCache.Api,
    baseUrl: lishogi.common.config.BaseUrl
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: akka.actor.ActorSystem,
    mat: akka.stream.Materializer,
    idGenerator: lishogi.game.IdGenerator,
    mode: play.api.Mode
) {

  private val colls = wire[SwissColls]

  private val sheetApi = wire[SwissSheetApi]

  private lazy val rankingApi: SwissRankingApi = wire[SwissRankingApi]

  val trf: SwissTrf = wire[SwissTrf]

  private val pairingSystem = new PairingSystem(trf, rankingApi, appConfig.get[String]("swiss.bbpairing"))

  private val scoring = wire[SwissScoring]

  private val director = wire[SwissDirector]

  private val boardApi = wire[SwissBoardApi]

  private val statsApi = wire[SwissStatsApi]

  val api = wire[SwissApi]

  private lazy val socket = wire[SwissSocket]

  def version(swissId: Swiss.Id): Fu[SocketVersion] =
    socket.rooms.ask[SocketVersion](swissId.value)(GetVersion)

  lazy val standingApi = wire[SwissStandingApi]

  lazy val json = wire[SwissJson]

  lazy val forms = wire[SwissForm]

  private lazy val cache: SwissCache = wire[SwissCache]

  lazy val getName = new GetSwissName(cache.name.sync)

  lishogi.common.Bus.subscribeFun(
    "finishGame",
    "adjustCheater",
    "adjustBooster",
    "teamKick"
  ) {
    case lishogi.game.actorApi.FinishGame(game, _, _)           => api.finishGame(game)
    case lishogi.hub.actorApi.team.KickFromTeam(teamId, userId) => api.kickFromTeam(teamId, userId)
    case lishogi.hub.actorApi.mod.MarkCheater(userId, true)     => api.kickLame(userId)
    case lishogi.hub.actorApi.mod.MarkBooster(userId)           => api.kickLame(userId)
  }

  ResilientScheduler(
    every = Every(1 seconds),
    atMost = AtMost(20 seconds),
    initialDelay = 20 seconds
  ) { api.startPendingRounds }

  ResilientScheduler(
    every = Every(10 seconds),
    atMost = AtMost(15 seconds),
    initialDelay = 20 seconds
  ) { api.checkOngoingGames }
}

private class SwissColls(db: lishogi.db.Db) {
  val swiss   = db(CollName("swiss"))
  val player  = db(CollName("swiss_player"))
  val pairing = db(CollName("swiss_pairing"))
}
