package lishogi.fishnet

import akka.actor._
import com.softwaremill.macwire._
import io.lettuce.core._
import io.methvin.play.autoconfig._
import play.api.Configuration

import lishogi.common.Bus
import lishogi.common.config._
import lishogi.game.Game

@Module
private class FishnetConfig(
    @ConfigName("collection.analysis") val analysisColl: CollName,
    @ConfigName("collection.client") val clientColl: CollName,
    @ConfigName("actor.name") val actorName: String,
    @ConfigName("offline_mode") val offlineMode: Boolean,
    @ConfigName("analysis.nodes") val analysisNodes: Int,
    @ConfigName("move.plies") val movePlies: Int,
    @ConfigName("client_min_version") val clientMinVersion: String
)

@Module
final class Env(
    appConfig: Configuration,
    uciMemo: lishogi.game.UciMemo,
    requesterApi: lishogi.analyse.RequesterApi,
    evalCacheApi: lishogi.evalCache.EvalCacheApi,
    gameRepo: lishogi.game.GameRepo,
    analysisRepo: lishogi.analyse.AnalysisRepo,
    db: lishogi.db.Db,
    cacheApi: lishogi.memo.CacheApi,
    sink: lishogi.analyse.Analyser,
    shutdown: akka.actor.CoordinatedShutdown
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: ActorSystem
) {

  private val config = appConfig.get[FishnetConfig]("fishnet")(AutoConfig.loader)

  private lazy val analysisColl = db(config.analysisColl)

  private lazy val clientVersion = new Client.ClientVersion(config.clientMinVersion)

  private lazy val repo = new FishnetRepo(
    analysisColl = analysisColl,
    clientColl = db(config.clientColl),
    cacheApi = cacheApi
  )

  private lazy val moveDb: MoveDB = wire[MoveDB]

  private lazy val monitor: Monitor = wire[Monitor]

  private lazy val evalCache = wire[FishnetEvalCache]

  private lazy val analysisBuilder = wire[AnalysisBuilder]

  private lazy val apiConfig = FishnetApi.Config(
    offlineMode = config.offlineMode,
    analysisNodes = config.analysisNodes
  )

  private lazy val socketExists: Game.ID => Fu[Boolean] = id =>
    Bus.ask[Boolean]("roundSocket")(lishogi.hub.actorApi.map.Exists(id, _))

  lazy val api: FishnetApi = wire[FishnetApi]

  lazy val player = {
    def mk = (plies: Int) => wire[Player]
    mk(config.movePlies)
  }

  private val limiter = wire[Limiter]

  lazy val analyser = wire[Analyser]

  lazy val aiPerfApi = wire[AiPerfApi]

  wire[Cleaner]

  wire[MainWatcher]

  // api actor
  system.actorOf(
    Props(new Actor {
      def receive = {
        case lishogi.hub.actorApi.fishnet.AutoAnalyse(gameId) =>
          analyser(gameId, Work.Sender(userId = none, ip = none, mod = false, system = true))
        case req: lishogi.hub.actorApi.fishnet.StudyChapterRequest => analyser study req
      }
    }),
    name = config.actorName
  )

  private def disable(username: String) =
    repo toKey username flatMap { repo.enableClient(_, false) }

  def cli =
    new lishogi.common.Cli {
      def process = {
        case "fishnet" :: "client" :: "create" :: userId :: Nil =>
          api.createClient(Client.UserId(userId.toLowerCase)) map { client =>
            Bus.publish(lishogi.hub.actorApi.fishnet.NewKey(userId, client.key.value), "fishnet")
            s"Created key: ${(client.key.value)} for: $userId"
          }
        case "fishnet" :: "client" :: "delete" :: key :: Nil =>
          repo toKey key flatMap repo.deleteClient inject "done!"
        case "fishnet" :: "client" :: "enable" :: key :: Nil =>
          repo toKey key flatMap { repo.enableClient(_, true) } inject "done!"
        case "fishnet" :: "client" :: "disable" :: key :: Nil => disable(key) inject "done!"
      }
    }

  Bus.subscribeFun("adjustCheater", "adjustBooster", "shadowban") {
    case lishogi.hub.actorApi.mod.MarkCheater(userId, true) => disable(userId)
    case lishogi.hub.actorApi.mod.MarkBooster(userId)       => disable(userId)
    case lishogi.hub.actorApi.mod.Shadowban(userId, true)   => disable(userId)
  }
}
