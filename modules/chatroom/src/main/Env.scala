package lila.chatroom

import scala.concurrent.duration._

import akka.actor.ActorSystem
import com.softwaremill.macwire._

import lila.socket.Socket.GetVersion
import lila.socket.Socket.SocketVersion
import lila.user.User

@Module
final class Env(
    remoteSocketApi: lila.socket.RemoteSocket,
    chatApi: lila.chat.ChatApi,
)(implicit
    system: ActorSystem,
    ec: scala.concurrent.ExecutionContext,
) {

  val lishogiChatroomId = "lishogi-chatroom"

  private val chatroomSocket = wire[ChatroomSocket]

  def get(me: Option[User]) = chatApi.cached
    .findMine(lila.chat.Chat.Id(lishogiChatroomId), me)

  def version(id: lila.chat.Chat.Id) =
    chatroomSocket.rooms.ask[SocketVersion](id.value)(GetVersion)

  system.scheduler.scheduleWithFixedDelay(80 seconds, 10 minutes) { () =>
    chatApi.clearInactive(lila.chat.Chat.Id(lishogiChatroomId), _.Chatroom).unit
  }

}
