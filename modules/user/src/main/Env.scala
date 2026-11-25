package lila.user

import play.api.Configuration

import akka.actor._
import com.softwaremill.macwire._
import io.methvin.play.autoconfig._

import lila.common.config._
import lila.db.dsl.Coll

private class UserConfig(
    @ConfigName("password.bpass.secret") val passwordBPassSecret: Secret,
)

@Module
final class Env(
    appConfig: Configuration,
    db: lila.db.Db,
    mongoCache: lila.memo.MongoCache.Api,
    cacheApi: lila.memo.CacheApi,
    isOnline: lila.socket.IsOnline,
    onlineIds: lila.socket.OnlineIds,
    mode: play.api.Mode,
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: ActorSystem,
) {

  private val config = appConfig.get[UserConfig]("user")(AutoConfig.loader)

  val repo = new UserRepo(db(CollName("user4")))

  val lightUserApi: LightUserApi = wire[LightUserApi]
  val lightUser                  = lightUserApi.async
  val lightUserSync              = lightUserApi.sync

  lazy val botIds = new GetBotIds(() => cached.botIds.get {})

  lazy val jsonView = wire[JsonView]

  lazy val noteApi = {
    def mk = (coll: Coll) => wire[NoteApi]
    mk(db(CollName("note")))
  }

  lazy val trophyApi =
    new TrophyApi(db(CollName("trophy")), db(CollName("trophyKind")), cacheApi)

  lazy val rankingApi = {
    def mk = (coll: Coll) => wire[RankingApi]
    mk(db(CollName("ranking")))
  }

  lazy val cached: Cached = wire[Cached]

  private lazy val passHasher = new PasswordHasher(
    secret = config.passwordBPassSecret,
    logRounds = 10,
    hashTimer = res => lila.common.Chronometer.syncMon(_.user.auth.hashTime)(res),
  )

  lazy val authenticator = wire[Authenticator]

  lazy val forms = wire[UserForm]

  lila.common.Bus.subscribeFuns(
    "disableUser" -> { case lila.hub.actorApi.mod.DisableUser(userId) =>
      repo.disable(userId, keepEmail = true).unit
    },
    "adjustCheater" -> { case lila.hub.actorApi.mod.MarkCheater(userId, true) =>
      rankingApi remove userId
      repo.setRoles(userId, Nil).unit
    },
    "adjustBooster" -> { case lila.hub.actorApi.mod.MarkBooster(userId) =>
      rankingApi.remove(userId).unit
    },
    "kickFromRankings" -> { case lila.hub.actorApi.mod.KickFromRankings(userId) =>
      rankingApi.remove(userId).unit
    },
    "gdprErase" -> { case User.GDPRErase(user) =>
      repo erase user
      noteApi erase user
      ()
    },
  )
}
