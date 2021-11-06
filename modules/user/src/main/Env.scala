package lishogi.user

import akka.actor._
import com.softwaremill.macwire._
import io.methvin.play.autoconfig._
import play.api.Configuration
import play.api.libs.ws.WSClient
import scala.concurrent.duration._

import lishogi.common.config._
import lishogi.common.LightUser
import lishogi.db.dsl.Coll

private class UserConfig(
    @ConfigName("online.ttl") val onlineTtl: FiniteDuration,
    @ConfigName("collection.user") val collectionUser: CollName,
    @ConfigName("collection.note") val collectionNote: CollName,
    @ConfigName("collection.trophy") val collectionTrophy: CollName,
    @ConfigName("collection.trophyKind") val collectionTrophyKind: CollName,
    @ConfigName("collection.ranking") val collectionRanking: CollName,
    @ConfigName("password.bpass.secret") val passwordBPassSecret: Secret
)

@Module
final class Env(
    appConfig: Configuration,
    db: lishogi.db.Db,
    mongoCache: lishogi.memo.MongoCache.Api,
    cacheApi: lishogi.memo.CacheApi,
    isOnline: lishogi.socket.IsOnline,
    onlineIds: lishogi.socket.OnlineIds
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: ActorSystem,
    ws: WSClient
) {

  private val config = appConfig.get[UserConfig]("user")(AutoConfig.loader)

  val repo = new UserRepo(db(config.collectionUser))

  val lightUserApi: LightUserApi = wire[LightUserApi]
  val lightUser                  = lightUserApi.async
  val lightUserSync              = lightUserApi.sync
  val isBotSync                  = new LightUser.IsBotSync(id => lightUserApi.sync(id).exists(_.isBot))

  lazy val botIds = new GetBotIds(() => cached.botIds.get {})

  lazy val jsonView = wire[JsonView]

  lazy val noteApi = {
    def mk = (coll: Coll) => wire[NoteApi]
    mk(db(config.collectionNote))
  }

  lazy val trophyApi = new TrophyApi(db(config.collectionTrophy), db(config.collectionTrophyKind), cacheApi)

  lazy val rankingApi = {
    def mk = (coll: Coll) => wire[RankingApi]
    mk(db(config.collectionRanking))
  }

  lazy val cached: Cached = wire[Cached]

  private lazy val passHasher = new PasswordHasher(
    secret = config.passwordBPassSecret,
    logRounds = 10,
    hashTimer = res => lishogi.common.Chronometer.syncMon(_.user.auth.hashTime)(res)
  )

  lazy val authenticator = wire[Authenticator]

  lazy val forms = wire[DataForm]

  lishogi.common.Bus.subscribeFuns(
    "adjustCheater" -> { case lishogi.hub.actorApi.mod.MarkCheater(userId, true) =>
      rankingApi remove userId
      repo.setRoles(userId, Nil)
    },
    "adjustBooster" -> { case lishogi.hub.actorApi.mod.MarkBooster(userId) =>
      rankingApi remove userId
    },
    "kickFromRankings" -> { case lishogi.hub.actorApi.mod.KickFromRankings(userId) =>
      rankingApi remove userId
    },
    "gdprErase" -> { case User.GDPRErase(user) =>
      repo erase user
      noteApi erase user
    }
  )
}
