package lishogi.msg

import com.softwaremill.macwire._

import lishogi.common.Bus
import lishogi.common.config._
import lishogi.user.User
import lishogi.hub.actorApi.socket.remote.TellUserIn

@Module
final class Env(
    db: lishogi.db.Db,
    lightUserApi: lishogi.user.LightUserApi,
    isOnline: lishogi.socket.IsOnline,
    userRepo: lishogi.user.UserRepo,
    userCache: lishogi.user.Cached,
    relationApi: lishogi.relation.RelationApi,
    prefApi: lishogi.pref.PrefApi,
    notifyApi: lishogi.notify.NotifyApi,
    cacheApi: lishogi.memo.CacheApi,
    spam: lishogi.security.Spam,
    chatPanic: lishogi.chat.ChatPanic,
    shutup: lishogi.hub.actors.Shutup
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: akka.actor.ActorSystem,
    scheduler: akka.actor.Scheduler
) {

  private val colls = wire[MsgColls]

  lazy val json = wire[MsgJson]

  private lazy val notifier = wire[MsgNotify]

  private lazy val security = wire[MsgSecurity]

  lazy val api: MsgApi = wire[MsgApi]

  lazy val search = wire[MsgSearch]

  lazy val compat = wire[MsgCompat]

  def cli =
    new lishogi.common.Cli {
      def process = { case "msg" :: "multi" :: orig :: dests :: words =>
        api.cliMultiPost(orig, dests.split(',').toIndexedSeq, words mkString " ")
      }
    }

  Bus.subscribeFuns(
    "msgSystemSend" -> { case lishogi.hub.actorApi.msg.SystemMsg(userId, text) =>
      api.systemPost(userId, text)
    },
    "remoteSocketIn:msgRead" -> { case TellUserIn(userId, msg) =>
      msg str "d" map User.normalize foreach { api.setRead(userId, _) }
    },
    "remoteSocketIn:msgSend" -> { case TellUserIn(userId, msg) =>
      for {
        obj  <- msg obj "d"
        dest <- obj str "dest" map User.normalize
        text <- obj str "text"
      } api.post(userId, dest, text)
    }
  )
}

private class MsgColls(db: lishogi.db.Db) {
  val thread = db(CollName("msg_thread"))
  val msg    = db(CollName("msg_msg"))
}
