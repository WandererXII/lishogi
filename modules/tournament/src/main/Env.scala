package lila.tournament

import scala.concurrent.duration._

import play.api.Configuration

import akka.actor._
import com.softwaremill.macwire._
import io.methvin.play.autoconfig._

import lila.common.config._
import lila.socket.Socket.GetVersion
import lila.socket.Socket.SocketVersion
import lila.user.User

@Module
private class TournamentConfig(
    @ConfigName("api_actor.name") val apiActorName: String,
)

@Module
final class Env(
    appConfig: Configuration,
    db: lila.db.Db,
    mongoCache: lila.memo.MongoCache.Api,
    cacheApi: lila.memo.CacheApi,
    gameRepo: lila.game.GameRepo,
    userRepo: lila.user.UserRepo,
    proxyRepo: lila.round.GameProxyRepo,
    chatApi: lila.chat.ChatApi,
    tellRound: lila.round.TellRound,
    roundSocket: lila.round.RoundSocket,
    notifyApi: lila.notify.NotifyApi,
    lightUserApi: lila.user.LightUserApi,
    onStart: lila.round.OnStart,
    historyApi: lila.history.HistoryApi,
    remoteSocketApi: lila.socket.RemoteSocket,
    mode: play.api.Mode,
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: ActorSystem,
    mat: akka.stream.Materializer,
    idGenerator: lila.game.IdGenerator,
) {

  private val config = appConfig.get[TournamentConfig]("tournament")(AutoConfig.loader)

  private def scheduler = system.scheduler

  lazy val forms = wire[DataForm]

  private val playerColl = CollName("tournament_player")

  lazy val tournamentRepo  = new TournamentRepo(db(CollName("tournament2")), playerColl)
  lazy val pairingRepo     = new PairingRepo(db(CollName("tournament_pairing")))
  lazy val arrangementRepo = new ArrangementRepo(db(CollName("tournament_arrangement")))
  lazy val playerRepo      = new PlayerRepo(db(playerColl))
  private lazy val leaderboardRepo = new LeaderboardRepo(db(CollName("tournament_leaderboard")))

  lazy val cached: Cached = wire[Cached]

  lazy val pager: TournamentPager = wire[TournamentPager]

  lazy val verify = wire[Condition.Verify]

  lazy val winners: WinnersApi = wire[WinnersApi]

  lazy val statsApi = wire[TournamentStatsApi]

  lazy val shieldApi: TournamentShieldApi = wire[TournamentShieldApi]

  lazy val revolutionApi: RevolutionApi = wire[RevolutionApi]

  private lazy val duelStore = wire[DuelStore]

  private lazy val pause = wire[Pause]

  private lazy val socket = wire[TournamentSocket]

  private lazy val pairingSystem = wire[arena.PairingSystem]

  private lazy val apiCallbacks = TournamentApi.Callbacks(
    clearJsonViewCache = jsonView.clearCache,
    clearWinnersCache = winners.clearCache,
    clearTrophyCache = tour =>
      {
        if (tour.isShield) scheduler.scheduleOnce(10 seconds) { shieldApi.clear() }
        else if (Revolution is tour)
          scheduler.scheduleOnce(10 seconds) { revolutionApi.clear() }.unit
      }.unit,
    indexLeaderboard = leaderboardIndexer.indexOne _,
  )

  lazy val api: TournamentApi = wire[TournamentApi]

  lazy val crudApi = wire[crud.CrudApi]

  lazy val jsonView: JsonView = wire[JsonView]

  lazy val apiJsonView = wire[ApiJsonView]

  lazy val notifier = wire[Notifier]
  system.scheduler.scheduleWithFixedDelay(100 seconds, 2 minutes) { () =>
    notifier.tours.unit
  }
  // system.scheduler.scheduleWithFixedDelay(110 seconds, 2 minutes) { () =>
  //   notifier.arrangements.unit
  // }

  lazy val leaderboardApi = wire[LeaderboardApi]

  lazy val standingApi = wire[TournamentStandingApi]

  private lazy val leaderboardIndexer: LeaderboardIndexer = wire[LeaderboardIndexer]

  private lazy val gameMaker = wire[GameMaker]

  lazy val getTourName = new GetTourName((id, lang) => cached.nameCache.sync(id -> lang))
  lazy val getArrName  = new GetArrName(id => cached.arrangementNameCache.sync(id))

  system.actorOf(Props(wire[ApiActor]), name = config.apiActorName)

  system.actorOf(Props(wire[CreatedOrganizer]))

  system.actorOf(Props(wire[StartedArenaOrganizer]))

  system.actorOf(Props(wire[StartedNonArenaOrganizer]))

  private lazy val schedulerActor = system.actorOf(Props(wire[TournamentScheduler]))
  scheduler.scheduleWithFixedDelay(1 minute, 5 minutes) { () =>
    schedulerActor ! TournamentScheduler.ScheduleNow
  }

  scheduler.scheduleWithFixedDelay(1 minute, 1 minute) { () =>
    tournamentRepo.countCreated foreach { lila.mon.tournament.created.update(_) }
  }

  def version(tourId: Tournament.ID): Fu[SocketVersion] =
    socket.rooms.ask[SocketVersion](tourId)(GetVersion)

  // is that user playing a game of this tournament
  // or hanging out in the tournament lobby (joined or not)
  // or the creator of the tournament
  def hasUser(tourId: Tournament.ID, userId: User.ID): Fu[Boolean] =
    fuccess(socket.hasUser(tourId, userId)) >>|
      tournamentRepo.isCreator(tourId, userId) >>|
      pairingRepo.isPlaying(tourId, userId) >>|
      arrangementRepo.isPlaying(tourId, userId)

  def cli =
    new lila.common.Cli {
      def process = { case "tournament" :: "leaderboard" :: "generate" :: Nil =>
        leaderboardIndexer.generateAll inject "Done!"
      }
    }
}
