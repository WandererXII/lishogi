package lila.shoginet

import play.api.Configuration

import akka.actor._
import com.softwaremill.macwire._
import io.methvin.play.autoconfig._

import lila.common.Bus
import lila.common.config._
import lila.db.dsl._
import lila.game.Game

@Module
private class ShoginetConfig(
    @ConfigName("actor.name") val actorName: String,
    @ConfigName("anon_mode") val anonMode: Boolean,
    @ConfigName("analysis.nodes") val analysisNodes: Int,
    @ConfigName("move.plies") val movePlies: Int,
    @ConfigName("client_min_version") val clientMinVersion: String,
)

case class ShoginetColls(
    analysis: Coll,
    puzzle: Coll,
    client: Coll,
)

@Module
final class Env(
    appConfig: Configuration,
    requesterApi: lila.analyse.RequesterApi,
    evalCacheApi: lila.evalCache.EvalCacheApi,
    gameRepo: lila.game.GameRepo,
    analysisRepo: lila.analyse.AnalysisRepo,
    db: lila.db.Db,
    cacheApi: lila.memo.CacheApi,
    sink: lila.analyse.Analyser,
    puzzle: lila.puzzle.PuzzleApi,
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: ActorSystem,
) {

  private val config = appConfig.get[ShoginetConfig]("shoginet")(AutoConfig.loader)

  private val analysisColl = db(CollName("fishnet_analysis"))

  private lazy val colls = ShoginetColls(
    analysis = analysisColl,
    puzzle = db(CollName("fishnet_puzzle")),
    client = db(CollName("fishnet_client")),
  )

  private lazy val repo = new ShoginetRepo(
    colls = colls,
    cacheApi = cacheApi,
  )

  private lazy val moveDb: MoveDB = wire[MoveDB]

  private lazy val monitor: Monitor = wire[Monitor]

  private lazy val evalCache = wire[ShoginetEvalCache]

  private lazy val analysisBuilder = wire[AnalysisBuilder]

  private lazy val apiConfig = ShoginetApi.Config(
    anonMode = config.anonMode,
    analysisNodes = config.analysisNodes,
    clientVersion = new Client.ClientVersion(config.clientMinVersion),
  )

  private lazy val socketExists: Game.ID => Fu[Boolean] = id =>
    Bus.ask[Boolean]("roundSocket")(lila.hub.actorApi.map.Exists(id, _))

  lazy val api: ShoginetApi = wire[ShoginetApi]

  lazy val player = {
    def mk = (plies: Int) => wire[Player]
    mk(config.movePlies)
  }

  private val limiter = wire[Limiter]

  lazy val analyser = wire[Analyser]

  wire[Cleaner]

  wire[MainWatcher]

  // api actor
  system.actorOf(
    Props(new Actor {
      def receive = {
        case lila.hub.actorApi.shoginet.AutoAnalyse(gameId) =>
          analyser(
            gameId,
            Work.Sender(userId = none, postGameStudy = none, ip = none, mod = false, system = true),
          ).unit
        case lila.hub.actorApi.shoginet.PostGameStudyRequest(userId, gameId, studyId, chapterId) =>
          analyser
            .postGameStudy(
              gameId,
              Work.Sender(
                userId = userId.some,
                postGameStudy = lila.analyse.Analysis.PostGameStudy(studyId, chapterId).some,
                ip = none,
                mod = false,
                system = false,
              ),
            )
            .unit
        case req: lila.hub.actorApi.shoginet.StudyChapterRequest => analyser.study(req).unit
      }
    }),
    name = config.actorName,
  )

  private def disable(username: String) =
    repo toKey username flatMap { repo.enableClient(_, enabled = false) }

  def cli =
    new lila.common.Cli {
      def process = {
        case "shoginet" :: "client" :: "create" :: userId :: Nil =>
          api.createClient(Client.UserId(userId.toLowerCase)) map { client =>
            Bus.publish(lila.hub.actorApi.shoginet.NewKey(userId, client.key.value), "shoginet")
            s"Created key: ${(client.key.value)} for: $userId"
          }
        case "shoginet" :: "client" :: "delete" :: key :: Nil =>
          repo toKey key flatMap repo.deleteClient inject "done!"
        case "shoginet" :: "client" :: "enable" :: key :: Nil =>
          repo toKey key flatMap { repo.enableClient(_, enabled = true) } inject "done!"
        case "shoginet" :: "client" :: "disable" :: key :: Nil => disable(key) inject "done!"
      }
    }

  Bus.subscribeFun("adjustCheater", "adjustBooster", "shadowban") {
    case lila.hub.actorApi.mod.MarkCheater(userId, true) => disable(userId).unit
    case lila.hub.actorApi.mod.MarkBooster(userId)       => disable(userId).unit
    case lila.hub.actorApi.mod.Shadowban(userId, true)   => disable(userId).unit
  }
}
