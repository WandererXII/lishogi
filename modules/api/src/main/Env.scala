package lishogi.api

import akka.actor._
import com.softwaremill.macwire._
import play.api.libs.ws.WSClient
import play.api.{ Configuration, Mode }
import scala.concurrent.duration._

import lishogi.common.config._

@Module
final class Env(
    appConfig: Configuration,
    net: NetConfig,
    securityEnv: lishogi.security.Env,
    teamSearchEnv: lishogi.teamSearch.Env,
    forumSearchEnv: lishogi.forumSearch.Env,
    teamEnv: lishogi.team.Env,
    puzzleEnv: lishogi.puzzle.Env,
    explorerEnv: lishogi.explorer.Env,
    fishnetEnv: lishogi.fishnet.Env,
    studyEnv: lishogi.study.Env,
    studySearchEnv: lishogi.studySearch.Env,
    coachEnv: lishogi.coach.Env,
    evalCacheEnv: lishogi.evalCache.Env,
    planEnv: lishogi.plan.Env,
    gameEnv: lishogi.game.Env,
    roundEnv: lishogi.round.Env,
    bookmarkApi: lishogi.bookmark.BookmarkApi,
    prefApi: lishogi.pref.PrefApi,
    playBanApi: lishogi.playban.PlaybanApi,
    userEnv: lishogi.user.Env,
    streamerEnv: lishogi.streamer.Env,
    relationEnv: lishogi.relation.Env,
    analyseEnv: lishogi.analyse.Env,
    lobbyEnv: lishogi.lobby.Env,
    simulEnv: lishogi.simul.Env,
    tourEnv: lishogi.tournament.Env,
    swissEnv: lishogi.swiss.Env,
    onlineApiUsers: lishogi.bot.OnlineApiUsers,
    challengeEnv: lishogi.challenge.Env,
    msgEnv: lishogi.msg.Env,
    cacheApi: lishogi.memo.CacheApi,
    ws: WSClient,
    val mode: Mode
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: ActorSystem
) {

  val config = ApiConfig loadFrom appConfig
  import config.apiToken

  lazy val notationDump: NotationDump = wire[NotationDump]

  lazy val userApi = wire[UserApi]

  lazy val gameApi = wire[GameApi]

  lazy val realPlayers = wire[RealPlayerApi]

  lazy val gameApiV2 = wire[GameApiV2]

  lazy val userGameApi = wire[UserGameApi]

  lazy val roundApi = wire[RoundApi]

  lazy val lobbyApi = wire[LobbyApi]

  lazy val eventStream = wire[EventStream]

  lazy val cli = wire[Cli]

  lazy val influxEvent = new InfluxEvent(
    ws = ws,
    endpoint = config.influxEventEndpoint,
    env = config.influxEventEnv
  )
  if (mode == Mode.Prod && false) system.scheduler.scheduleOnce(5 seconds)(influxEvent.start()) // yep...

  system.scheduler.scheduleWithFixedDelay(20 seconds, 10 seconds) { () =>
    lishogi.mon.bus.classifiers.update(lishogi.common.Bus.size)
  }
}
