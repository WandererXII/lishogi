package lishogi.chat

import akka.actor.ActorSystem
import com.softwaremill.macwire._
import io.methvin.play.autoconfig._
import play.api.Configuration
import scala.concurrent.duration.FiniteDuration

import lishogi.common.config._

private case class ChatConfig(
    @ConfigName("collection.chat") chatColl: CollName,
    @ConfigName("collection.timeout") timeoutColl: CollName,
    @ConfigName("max_lines") maxLines: Chat.MaxLines,
    @ConfigName("actor.name") actorName: String,
    @ConfigName("timeout.duration") timeoutDuration: FiniteDuration,
    @ConfigName("timeout.check_every") timeoutCheckEvery: FiniteDuration
)

@Module
final class Env(
    appConfig: Configuration,
    netDomain: NetDomain,
    userRepo: lishogi.user.UserRepo,
    db: lishogi.db.Db,
    flood: lishogi.security.Flood,
    spam: lishogi.security.Spam,
    shutup: lishogi.hub.actors.Shutup,
    mod: lishogi.hub.actors.Mod,
    cacheApi: lishogi.memo.CacheApi
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: ActorSystem
) {

  implicit private val maxPerLineLoader = intLoader(Chat.MaxLines.apply)
  private val config                    = appConfig.get[ChatConfig]("chat")(AutoConfig.loader)
  import config._

  lazy val timeout = new ChatTimeout(
    coll = db(timeoutColl),
    duration = timeoutDuration
  )

  lazy val api = new ChatApi(
    coll = db(chatColl),
    userRepo = userRepo,
    chatTimeout = timeout,
    flood = flood,
    spam = spam,
    shutup = shutup,
    modActor = mod,
    cacheApi = cacheApi,
    maxLinesPerChat = maxLines,
    netDomain = netDomain
  )

  lazy val panic = wire[ChatPanic]

  system.scheduler.scheduleWithFixedDelay(timeoutCheckEvery, timeoutCheckEvery) { () =>
    timeout.checkExpired foreach api.userChat.reinstate
  }
}
