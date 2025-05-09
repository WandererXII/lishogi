package lila.lobby

import scala.concurrent.Promise
import scala.concurrent.duration._

import cats.implicits._
import org.joda.time.DateTime

import lila.common.AtMost
import lila.common.Bus
import lila.common.Every
import lila.common.config.Max
import lila.game.Game
import lila.hub.Trouper
import lila.lobby.actorApi._
import lila.socket.Socket.Sri
import lila.socket.Socket.Sris
import lila.user.User

final private class LobbyTrouper(
    seekApi: SeekApi,
    biter: Biter,
    gameCache: lila.game.Cached,
    gameRepo: lila.game.GameRepo,
    maxPlaying: Max,
    playbanApi: lila.playban.PlaybanApi,
    onStart: lila.round.OnStart,
)(implicit ec: scala.concurrent.ExecutionContext)
    extends Trouper {

  import LobbyTrouper._

  private var remoteDisconnectAllAt = DateTime.now

  private var socket: Trouper = Trouper.stub

  val process: Trouper.Receive = {

    // solve circular reference
    case SetSocket(trouper) => socket = trouper

    case msg @ AddHook(hook) =>
      lila.mon.lobby.hook.create.increment()
      HookRepo bySri hook.sri foreach remove
      hook.sid ?? { sid =>
        HookRepo bySid sid foreach remove
      }
      findCompatibleHook(hook) match {
        case Some(h) => biteHook(h.id, hook.sri, hook.user)
        case None =>
          HookRepo save msg.hook
          socket ! msg
      }

    case msg @ AddSeek(seek) =>
      lila.mon.lobby.seek.create.increment()
      findCompatibleSeek(seek) foreach {
        case Some(s) => this ! BiteSeek(s.id, seek.user)
        case None    => this ! SaveSeek(msg)
      }

    case SaveSeek(msg) =>
      seekApi.insert(msg.seek)
      socket ! msg

    case CancelHook(sri) =>
      HookRepo bySri sri foreach remove

    case CancelSeek(seekId, user) =>
      seekApi.removeBy(seekId, user.id)
      socket ! RemoveSeek(seekId)

    case BiteHook(hookId, sri, user) =>
      NoPlayban(user) {
        biteHook(hookId, sri, user)
      }

    case BiteSeek(seekId, user) =>
      NoPlayban(user.some) {
        gameCache.nbPlaying(user.id) foreach { nbPlaying =>
          if (maxPlaying > nbPlaying) {
            lila.mon.lobby.seek.join.increment()
            seekApi find seekId foreach {
              _ foreach { seek =>
                biter(seek, user) foreach this.!
              }
            }
          }
        }
      }

    case msg @ JoinHook(_, hook, game, _) =>
      onStart(game.id)
      socket ! msg
      remove(hook)

    case msg @ JoinSeek(_, seek, game, _) =>
      onStart(game.id)
      seekApi.archive(seek, game.id)
      socket ! msg
      socket ! RemoveSeek(seek.id)

    case LeaveAll => remoteDisconnectAllAt = DateTime.now

    case Tick(promise) =>
      HookRepo.truncateIfNeeded()
      socket
        .ask[Sris](GetSrisP)
        .chronometer
        .logIfSlow(100, logger) { r =>
          s"GetSris size=${r.sris.size}"
        }
        .mon(_.lobby.socket.getSris)
        .result
        .logFailure(logger, err => s"broom cannot get sris from socket: $err")
        .foreach { this ! WithPromise(_, promise) }

    case WithPromise(Sris(sris), promise) =>
      val fewSecondsAgo = DateTime.now minusSeconds 5
      if (remoteDisconnectAllAt isBefore fewSecondsAgo) this ! RemoveHooks({
        (HookRepo notInSris sris).filter { h =>
          !h.boardApi && (h.createdAt isBefore fewSecondsAgo)
        } ++ HookRepo.cleanupOld
      }.toSet)
      lila.mon.lobby.socket.member.update(sris.size)
      lila.mon.lobby.hook.size.record(HookRepo.size)
      lila.mon.trouper.queueSize("lobby").update(queueSize)
      promise.success(())

    case RemoveHooks(hooks) => hooks foreach remove

    case Resync => socket ! HookIds(HookRepo.vector.map(_.id))

    case HookSub(member, true) =>
      socket ! AllHooksFor(member, HookRepo.vector.filter { biter.showHookTo(_, member) })
  }

  private def NoPlayban(user: Option[LobbyUser])(f: => Unit): Unit = {
    user.?? { u =>
      playbanApi.currentBan(u.id)
    } foreach {
      case None => f
      case _    =>
    }
  }

  private def biteHook(hookId: String, sri: Sri, user: Option[LobbyUser]) =
    HookRepo byId hookId foreach { hook =>
      remove(hook)
      HookRepo bySri sri foreach remove
      biter(hook, sri, user) foreach this.!
    }

  private def findCompatibleHook(hook: Hook): Option[Hook] =
    HookRepo findCompatible hook find { existing =>
      biter.canAutoJoin(existing, hook.user) && !(
        (existing.user, hook.user).mapN((_, _)) ?? { case (u1, u2) =>
          recentlyAbortedUserIdPairs.exists(u1.id, u2.id)
        }
      )
    }

  def registerAbortedGame(g: Game) = recentlyAbortedUserIdPairs register g

  private object recentlyAbortedUserIdPairs {
    private val cache                                     = new lila.memo.ExpireSetMemo(1 hour)
    private def makeKey(u1: User.ID, u2: User.ID): String = if (u1 < u2) s"$u1/$u2" else s"$u2/$u1"
    def register(g: Game) =
      if (g.fromLobby)
        for {
          sp <- g.sentePlayer.userId
          gp <- g.gotePlayer.userId
        } cache.put(makeKey(sp, gp))
    def exists(u1: User.ID, u2: User.ID) = cache.get(makeKey(u1, u2))
  }

  private def findCompatibleSeek(seek: Seek): Fu[Option[Seek]] =
    for {
      candidates <- seekApi.forUser(seek.user).map(_.filter(_.compatibleWith(seek)))
      currentplyPlaying <- candidates.nonEmpty ?? gameRepo.playingCorresOpponentsOfVariant(
        seek.user.id,
        seek.realVariant,
      )
    } yield (candidates.find(c => !currentplyPlaying.contains(c.user.id)))

  private def remove(hook: Hook) = {
    HookRepo remove hook
    socket ! RemoveHook(hook.id)
    Bus.publish(RemoveHook(hook.id), s"hookRemove:${hook.id}")
  }
}

private object LobbyTrouper {

  case class SetSocket(trouper: Trouper)

  private case class Tick(promise: Promise[Unit])

  private case class WithPromise[A](value: A, promise: Promise[Unit])

  def start(
      broomPeriod: FiniteDuration,
      resyncIdsPeriod: FiniteDuration,
  )(
      makeTrouper: () => LobbyTrouper,
  )(implicit ec: scala.concurrent.ExecutionContext, system: akka.actor.ActorSystem) = {
    val trouper = makeTrouper()
    Bus.subscribe(trouper, "lobbyTrouper")
    system.scheduler.scheduleWithFixedDelay(15 seconds, resyncIdsPeriod)(() =>
      trouper ! actorApi.Resync,
    )
    lila.common.ResilientScheduler(
      every = Every(broomPeriod),
      atMost = AtMost(10 seconds),
      initialDelay = 7 seconds,
    ) { trouper.ask[Unit](Tick) }
    trouper
  }
}
