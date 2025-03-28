package lila.socket

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.util.chaining._

import play.api.libs.json._

import akka.actor.ActorSystem
import akka.actor.CoordinatedShutdown
import io.lettuce.core._
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection

import shogi.Centis

import lila.common.Bus
import lila.common.Lilakka
import lila.hub.actorApi.Announce
import lila.hub.actorApi.relation.Follow
import lila.hub.actorApi.relation.UnFollow
import lila.hub.actorApi.round.Mlat
import lila.hub.actorApi.security.CloseAccount
import lila.hub.actorApi.socket.ApiUserIsOnline
import lila.hub.actorApi.socket.SendTo
import lila.hub.actorApi.socket.SendTos
import lila.hub.actorApi.socket.remote.TellSriIn
import lila.hub.actorApi.socket.remote.TellSriOut
import lila.hub.actorApi.socket.remote.TellUserIn
import lila.socket.Socket.Sri

final class RemoteSocket(
    redisClient: RedisClient,
    notification: lila.hub.actors.Notification,
    shutdown: CoordinatedShutdown,
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: ActorSystem,
) {

  import RemoteSocket.Protocol._
  import RemoteSocket._

  private var stopping = false

  private type UserIds = Set[String]

  private val requests = new ConcurrentHashMap[Int, Promise[String]](32)

  def request[R](sendReq: Int => Unit, readRes: String => R): Fu[R] = {
    val id = lila.common.ThreadLocalRandom.nextPositiveInt()
    sendReq(id)
    val promise = Promise[String]()
    requests.put(id, promise)
    promise.future map readRes
  }

  val onlineUserIds: AtomicReference[Set[String]] = new AtomicReference(Set("lishogi"))

  val baseHandler: Handler = {
    case In.ConnectUser(userId) =>
      onlineUserIds.getAndUpdate(_ + userId).unit
    case In.DisconnectUsers(userIds) =>
      onlineUserIds.getAndUpdate(_ -- userIds).unit
    case In.NotifiedBatch(userIds) => notification ! lila.hub.actorApi.notify.NotifiedBatch(userIds)
    case In.Lags(lags) =>
      lags foreach (UserLagCache.put _).tupled
      // this shouldn't be necessary... ensure that users are known to be online
      onlineUserIds.getAndUpdate((x: UserIds) => x ++ lags.keys).unit
    case In.TellSri(sri, userId, typ, msg) =>
      Bus.publish(TellSriIn(sri.value, userId, msg), s"remoteSocketIn:$typ")
    case In.TellUser(userId, typ, msg) =>
      Bus.publish(TellUserIn(userId, msg), s"remoteSocketIn:$typ")
    case In.WsBoot =>
      logger.warn("Remote socket boot")
      onlineUserIds set Set("lishogi")
    case In.ReqResponse(reqId, response) =>
      requests
        .computeIfPresent(
          reqId,
          (_: Int, promise: Promise[String]) => {
            promise success response
            null // remove from promises
          },
        )
        .unit
  }

  Bus.subscribeFun(
    "socketUsers",
    "announce",
    "mlat",
    "sendToFlag",
    "remoteSocketOut",
    "accountClose",
    "shadowban",
    "impersonate",
    "relation",
    "onlineApiUsers",
  ) {
    case SendTos(userIds, payload) =>
      val connectedUsers = userIds intersect onlineUserIds.get
      if (connectedUsers.nonEmpty) send(Out.tellUsers(connectedUsers, payload))
    case SendTo(userId, payload) if onlineUserIds.get.contains(userId) =>
      send(Out.tellUser(userId, payload))
    case Announce(_, _, json) =>
      send(Out.tellAll(Json.obj("t" -> "announce", "d" -> json)))
    case Mlat(micros) =>
      send(Out.mlat(micros))
    case Socket.SendToFlag(flag, payload) =>
      send(Out.tellFlag(flag, payload))
    case TellSriOut(sri, payload) =>
      send(Out.tellSri(Sri(sri), payload))
    case CloseAccount(userId) =>
      send(Out.disconnectUser(userId))
    case lila.hub.actorApi.mod.Shadowban(userId, v) =>
      send(Out.setTroll(userId, v))
    case lila.hub.actorApi.mod.Impersonate(userId, modId) =>
      send(Out.impersonate(userId, modId))
    case ApiUserIsOnline(userId, value) =>
      send(Out.apiUserOnline(userId, value))
      if (value) onlineUserIds.getAndUpdate(_ + userId).unit
    case Follow(u1, u2)   => send(Out.follow(u1, u2))
    case UnFollow(u1, u2) => send(Out.unfollow(u1, u2))
  }

  final class StoppableSender(conn: StatefulRedisPubSubConnection[String, String], channel: Channel)
      extends Sender {
    def apply(msg: String): Unit = if (!stopping) conn.async.publish(channel, msg).unit
  }

  def makeSender(channel: Channel): Sender =
    new StoppableSender(redisClient.connectPubSub(), channel)

  private val send: Send = makeSender("site-out").apply _

  def subscribe(channel: Channel, reader: In.Reader)(handler: Handler): Future[Unit] = {
    val conn = redisClient.connectPubSub()
    conn.addListener(new pubsub.RedisPubSubAdapter[String, String] {
      override def message(_channel: String, message: String): Unit =
        reader(RawMsg(message)) collect handler match {
          case Some(_) => // processed
          case None    => logger.warn(s"Unhandled $channel $message")
        }
    })
    val subPromise = Promise[Unit]()
    conn.async.subscribe(channel).thenRun {
      new Runnable { def run() = subPromise.success(()) }
    }
    subPromise.future
  }

  Lilakka.shutdown(shutdown, _.PhaseBeforeServiceUnbind, "Telling lila-ws we're stopping") { () =>
    request[Unit](
      id => send(Protocol.Out.stop(id)),
      res => logger.info(s"lila-ws says: $res"),
    ).withTimeout(1 second)
      .addFailureEffect(e => logger.error("lila-ws stop", e))
      .nevermind
  }

  Lilakka.shutdown(shutdown, _.PhaseServiceUnbind, "Stopping the socket redis pool") { () =>
    Future {
      stopping = true
      redisClient.shutdown()
    }
  }
}

object RemoteSocket {

  private val logger = lila log "socket"

  type Send = String => Unit

  trait Sender {
    def apply(msg: String): Unit
  }

  object Protocol {

    final class RawMsg(val path: Path, val args: Args) {
      def get(nb: Int)(f: PartialFunction[Array[String], Option[In]]): Option[In] =
        f.applyOrElse(args.split(" ", nb), (_: Array[String]) => None)
      def all = args split ' '
    }
    def RawMsg(msg: String): RawMsg = {
      val parts = msg.split(" ", 2)
      new RawMsg(parts(0), ~parts.lift(1))
    }

    trait In
    object In {

      type Reader = RawMsg => Option[In]

      case object WsBoot                                                               extends In
      case class ConnectUser(userId: String)                                           extends In
      case class DisconnectUsers(userId: Iterable[String])                             extends In
      case class ConnectSris(cons: Iterable[(Sri, Option[String])])                    extends In
      case class DisconnectSris(sris: Iterable[Sri])                                   extends In
      case class NotifiedBatch(userIds: Iterable[String])                              extends In
      case class Lag(userId: String, lag: Centis)                                      extends In
      case class Lags(lags: Map[String, Centis])                                       extends In
      case class TellSri(sri: Sri, userId: Option[String], typ: String, msg: JsObject) extends In
      case class TellUser(userId: String, typ: String, msg: JsObject)                  extends In
      case class ReqResponse(reqId: Int, response: String)                             extends In

      val baseReader: Reader = raw =>
        raw.path match {
          case "connect/user"     => ConnectUser(raw.args).some
          case "disconnect/users" => DisconnectUsers(commas(raw.args)).some
          case "connect/sris" =>
            ConnectSris {
              commas(raw.args) map (_ split ' ') map { s =>
                (Sri(s(0)), s lift 1)
              }
            }.some
          case "disconnect/sris" => DisconnectSris(commas(raw.args) map Sri.apply).some
          case "notified/batch"  => NotifiedBatch(commas(raw.args)).some
          case "lag" =>
            raw.all pipe { s =>
              s lift 1 flatMap (_.toIntOption) map Centis.apply map { Lag(s(0), _) }
            }
          case "lags" =>
            Lags(commas(raw.args).flatMap {
              _ split ':' match {
                case Array(user, l) =>
                  l.toIntOption map { lag =>
                    user -> Centis(lag)
                  }
                case _ => None
              }
            }.toMap).some
          case "tell/sri" => raw.get(3)(tellSriMapper)
          case "tell/user" =>
            raw.get(2) { case Array(user, payload) =>
              for {
                obj <- Json.parse(payload).asOpt[JsObject]
                typ <- obj str "t"
              } yield TellUser(user, typ, obj)
            }
          case "req/response" =>
            raw.get(2) { case Array(reqId, response) =>
              reqId.toIntOption map { ReqResponse(_, response) }
            }
          case "boot" => WsBoot.some
          case _      => none
        }

      def tellSriMapper: PartialFunction[Array[String], Option[TellSri]] = {
        case Array(sri, user, payload) =>
          for {
            obj <- Json.parse(payload).asOpt[JsObject]
            typ <- obj str "t"
          } yield TellSri(Sri(sri), optional(user), typ, obj)
      }

      def commas(str: String): Array[String]    = if (str == "-") Array.empty else str split ','
      def boolean(str: String): Boolean         = str == "+"
      def optional(str: String): Option[String] = if (str == "-") None else Some(str)
    }

    object Out {
      def tellUser(userId: String, payload: JsObject) =
        s"tell/users $userId ${Json stringify payload}"
      def tellUsers(userIds: Set[String], payload: JsObject) =
        s"tell/users ${commas(userIds)} ${Json stringify payload}"
      def tellAll(payload: JsObject) =
        s"tell/all ${Json stringify payload}"
      def tellFlag(flag: String, payload: JsObject) =
        s"tell/flag $flag ${Json stringify payload}"
      def tellSri(sri: Sri, payload: JsValue) =
        s"tell/sri $sri ${Json stringify payload}"
      def tellSris(sris: Iterable[Sri], payload: JsValue) =
        s"tell/sris ${commas(sris)} ${Json stringify payload}"
      def mlat(micros: Int) =
        s"mlat ${((micros / 100) / 10d)}"
      def disconnectUser(userId: String) =
        s"disconnect/user $userId"
      def setTroll(userId: String, v: Boolean) =
        s"mod/troll/set $userId ${boolean(v)}"
      def impersonate(userId: String, by: Option[String]) =
        s"mod/impersonate $userId ${optional(by)}"
      def follow(u1: String, u2: String)       = s"rel/follow $u1 $u2"
      def unfollow(u1: String, u2: String)     = s"rel/unfollow $u1 $u2"
      def apiUserOnline(u: String, v: Boolean) = s"api/online $u ${boolean(v)}"
      def boot                                 = "boot"
      def stop(reqId: Int)                     = s"lila/stop $reqId"

      def commas(strs: Iterable[Any]): String = if (strs.isEmpty) "-" else strs mkString ","
      def boolean(v: Boolean): String         = if (v) "+" else "-"
      def color(c: shogi.Color): String       = c.fold("b", "w")
      def optional(str: Option[String])       = str getOrElse "-"
    }
  }

  type Channel = String
  type Path    = String
  type Args    = String
  type Handler = PartialFunction[Protocol.In, Unit]
}
