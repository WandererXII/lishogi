package lishogi.swiss

import scala.concurrent.duration._

import lishogi.hub.actorApi.team.IsLeader
import lishogi.hub.LateMultiThrottler
import lishogi.room.RoomSocket.{ Protocol => RP, _ }
import lishogi.socket.RemoteSocket.{ Protocol => P, _ }
import lishogi.socket.Socket.makeMessage

final private class SwissSocket(
    remoteSocketApi: lishogi.socket.RemoteSocket,
    chat: lishogi.chat.ChatApi
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: akka.actor.ActorSystem,
    mode: play.api.Mode
) {

  private val reloadThrottler = LateMultiThrottler(executionTimeout = none, logger = logger)

  def reload(id: Swiss.Id): Unit =
    reloadThrottler ! LateMultiThrottler.work(
      id = id.value,
      run = fuccess {
        send(RP.Out.tellRoom(RoomId(id.value), makeMessage("reload")))
      },
      delay = 1.seconds.some
    )

  lazy val rooms = makeRoomMap(send)

  subscribeChat(rooms, _.Swiss)

  private lazy val handler: Handler =
    roomHandler(
      rooms,
      chat,
      logger,
      roomId => _.Swiss(roomId.value).some,
      localTimeout = Some { (roomId, modId, _) =>
        lishogi.common.Bus.ask[Boolean]("teamIsLeader") { IsLeader(roomId.value, modId, _) }
      },
      chatBusChan = _.Swiss
    )

  private lazy val send: String => Unit = remoteSocketApi.makeSender("swiss-out").apply _

  remoteSocketApi.subscribe("swiss-in", RP.In.reader)(
    handler orElse remoteSocketApi.baseHandler
  ) >>- send(P.Out.boot)
}
