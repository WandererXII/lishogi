package lishogi.study

import com.softwaremill.macwire._
import play.api.Configuration
import play.api.libs.ws.WSClient

import lishogi.common.config._
import lishogi.socket.Socket.{ GetVersion, SocketVersion }
import lishogi.user.User

@Module
final class Env(
    appConfig: Configuration,
    ws: WSClient,
    lightUserApi: lishogi.user.LightUserApi,
    gamePgnDump: lishogi.game.NotationDump,
    divider: lishogi.game.Divider,
    gameRepo: lishogi.game.GameRepo,
    userRepo: lishogi.user.UserRepo,
    explorerImporter: lishogi.explorer.ExplorerImporter,
    notifyApi: lishogi.notify.NotifyApi,
    prefApi: lishogi.pref.PrefApi,
    relationApi: lishogi.relation.RelationApi,
    remoteSocketApi: lishogi.socket.RemoteSocket,
    timeline: lishogi.hub.actors.Timeline,
    fishnet: lishogi.hub.actors.Fishnet,
    chatApi: lishogi.chat.ChatApi,
    mongo: lishogi.db.Env,
    net: lishogi.common.config.NetConfig,
    cacheApi: lishogi.memo.CacheApi
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: akka.actor.ActorSystem,
    mat: akka.stream.Materializer,
    mode: play.api.Mode
) {

  private lazy val studyDb = mongo.asyncDb("study", appConfig.get[String]("study.mongodb.uri"))

  def version(studyId: Study.Id): Fu[SocketVersion] =
    socket.rooms.ask[SocketVersion](studyId.value)(GetVersion)

  def isConnected(studyId: Study.Id, userId: User.ID): Fu[Boolean] =
    socket.isPresent(studyId, userId)

  private def scheduler = system.scheduler

  private val socket: StudySocket = wire[StudySocket]

  lazy val studyRepo             = new StudyRepo(studyDb(CollName("study")))
  lazy val chapterRepo           = new ChapterRepo(studyDb(CollName("study_chapter_flat")))
  private lazy val topicRepo     = new StudyTopicRepo(studyDb(CollName("study_topic")))
  private lazy val userTopicRepo = new StudyUserTopicRepo(studyDb(CollName("study_user_topic")))

  lazy val jsonView = wire[JsonView]

  private lazy val pgnFetch = wire[PgnFetch]

  private lazy val chapterMaker = wire[ChapterMaker]

  private lazy val explorerGame = wire[ExplorerGame]

  private lazy val studyMaker = wire[StudyMaker]

  private lazy val studyInvite = wire[StudyInvite]

  private lazy val serverEvalRequester = wire[ServerEval.Requester]

  private lazy val sequencer = wire[StudySequencer]

  lazy val serverEvalMerger = wire[ServerEval.Merger]

  lazy val topicApi = wire[StudyTopicApi]

  lazy val api: StudyApi = wire[StudyApi]

  lazy val pager = wire[StudyPager]

  lazy val multiBoard = wire[StudyMultiBoard]

  lazy val notationDump = wire[NotationDump]

  lazy val gifExport = new GifExport(ws, appConfig.get[String]("game.gifUrl"))

  def cli =
    new lishogi.common.Cli {
      def process = { case "study" :: "rank" :: "reset" :: Nil =>
        api.resetAllRanks.map { count =>
          s"$count done"
        }
      }
    }

  lishogi.common.Bus.subscribeFun("gdprErase", "studyAnalysisProgress") {
    case lishogi.user.User.GDPRErase(user) => api erase user
    case lishogi.analyse.actorApi.StudyAnalysisProgress(analysis, complete) =>
      serverEvalMerger(analysis, complete)
  }
}
