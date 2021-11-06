package lishogi.simul

import akka.actor._
import akka.pattern.ask
import play.api.libs.json.Json
import scala.concurrent.duration._

import shogi.variant.Variant
import lishogi.common.{ Bus, Debouncer }
import lishogi.game.{ Game, GameRepo, PerfPicker }
import lishogi.hub.actorApi.lobby.ReloadSimuls
import lishogi.hub.actorApi.timeline.{ Propagate, SimulCreate, SimulJoin }
import lishogi.memo.CacheApi._
import lishogi.socket.Socket.SendToFlag
import lishogi.user.{ User, UserRepo }
import makeTimeout.short

final class SimulApi(
    userRepo: UserRepo,
    gameRepo: GameRepo,
    onGameStart: lishogi.round.OnStart,
    socket: SimulSocket,
    renderer: lishogi.hub.actors.Renderer,
    timeline: lishogi.hub.actors.Timeline,
    repo: SimulRepo,
    cacheApi: lishogi.memo.CacheApi
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: akka.actor.ActorSystem,
    mode: play.api.Mode
) {

  private val workQueue =
    new lishogi.hub.DuctSequencers(
      maxSize = 128,
      expiration = 10 minutes,
      timeout = 10 seconds,
      name = "simulApi"
    )

  def currentHostIds: Fu[Set[String]] = currentHostIdsCache.get {}

  def find  = repo.find _
  def byIds = repo.byIds _

  private val currentHostIdsCache = cacheApi.unit[Set[User.ID]] {
    _.refreshAfterWrite(5 minutes)
      .buildAsyncFuture { _ =>
        repo.allStarted dmap (_.view.map(_.hostId).toSet)
      }
  }

  def create(setup: SimulForm.Setup, me: User): Fu[Simul] = {
    val simul = Simul.make(
      name = setup.name,
      clock = SimulClock(
        config =
          shogi.Clock.Config(setup.clockTime * 60, setup.clockIncrement, setup.clockByoyomi, setup.periods),
        hostExtraTime = setup.clockExtra * 60
      ),
      variants = setup.variants.flatMap { shogi.variant.Variant(_) },
      position = setup.position
        .map {
          SimulForm.startingPosition(_, shogi.variant.Standard)
        }
        .filterNot(_.initial),
      host = me,
      color = setup.color,
      text = setup.text,
      team = setup.team
    )
    repo.create(simul, me.hasGames) >>- publish() >>- {
      timeline ! (Propagate(SimulCreate(me.id, simul.id, simul.fullName)) toFollowersOf me.id)
    } inject simul
  }

  def addApplicant(simulId: Simul.ID, user: User, variantKey: String): Funit =
    WithSimul(repo.findCreated, simulId) { simul =>
      if (simul.nbAccepted >= Game.maxPlayingRealtime) simul
      else {
        timeline ! (Propagate(SimulJoin(user.id, simul.id, simul.fullName)) toFollowersOf user.id)
        Variant(variantKey).filter(simul.variants.contains).fold(simul) { variant =>
          simul addApplicant SimulApplicant.make(
            SimulPlayer.make(
              user,
              variant,
              PerfPicker.mainOrDefault(
                speed = shogi.Speed(simul.clock.config.some),
                variant = variant,
                daysPerTurn = none
              )(user.perfs)
            )
          )
        }
      }
    }

  def removeApplicant(simulId: Simul.ID, user: User): Funit =
    WithSimul(repo.findCreated, simulId) { _ removeApplicant user.id }

  def accept(simulId: Simul.ID, userId: String, v: Boolean): Funit =
    userRepo byId userId flatMap {
      _ ?? { user =>
        WithSimul(repo.findCreated, simulId) { _.accept(user.id, v) }
      }
    }

  def start(simulId: Simul.ID): Funit =
    workQueue(simulId) {
      repo.findCreated(simulId) flatMap {
        _ ?? { simul =>
          simul.start ?? { started =>
            userRepo byId started.hostId orFail s"No such host: ${simul.hostId}" flatMap { host =>
              started.pairings.map(makeGame(started, host)).sequenceFu map { games =>
                games.headOption foreach { case (game, _) =>
                  socket.startSimul(simul, game)
                }
                games.foldLeft(started) { case (s, (g, hostColor)) =>
                  s.setPairingHostColor(g.id, hostColor)
                }
              }
            } flatMap { s =>
              Bus.publish(Simul.OnStart(s), "startSimul")
              update(s) >>- currentHostIdsCache.invalidateUnit()
            }
          }
        }
      }
    }

  def onPlayerConnection(game: Game, user: Option[User])(simul: Simul): Unit =
    if (user.exists(simul.isHost) && simul.isRunning) {
      repo.setHostGameId(simul, game.id)
      socket.hostIsOn(simul.id, game.id)
    }

  def abort(simulId: Simul.ID): Funit =
    workQueue(simulId) {
      repo.findCreated(simulId) flatMap {
        _ ?? { simul =>
          (repo remove simul) >>- socket.aborted(simul.id) >>- publish()
        }
      }
    }

  def setText(simulId: Simul.ID, text: String): Funit =
    workQueue(simulId) {
      repo.find(simulId) flatMap {
        _ ?? { simul =>
          repo.setText(simul, text) >>- socket.reload(simulId)
        }
      }
    }

  def finishGame(game: Game): Funit =
    game.simulId ?? { simulId =>
      workQueue(simulId) {
        repo.findStarted(simulId) flatMap {
          _ ?? { simul =>
            val simul2 = simul.updatePairing(
              game.id,
              _.finish(game.status, game.winnerUserId)
            )
            update(simul2) >>- {
              if (simul2.isFinished) onComplete(simul2)
            }
          }
        }
      }
    }

  private def onComplete(simul: Simul): Unit = {
    currentHostIdsCache.invalidateUnit()
    Bus.publish(
      lishogi.hub.actorApi.socket.SendTo(
        simul.hostId,
        lishogi.socket.Socket.makeMessage(
          "simulEnd",
          Json.obj(
            "id"   -> simul.id,
            "name" -> simul.name
          )
        )
      ),
      "socketUsers"
    )
  }

  def ejectCheater(userId: String): Unit =
    repo.allNotFinished foreach {
      _ foreach { oldSimul =>
        workQueue(oldSimul.id) {
          repo.findCreated(oldSimul.id) flatMap {
            _ ?? { simul =>
              (simul ejectCheater userId) ?? { simul2 =>
                update(simul2).void
              }
            }
          }
        }
      }
    }

  def idToName(id: Simul.ID): Fu[Option[String]] =
    repo find id dmap2 { _.fullName }

  private def makeGame(simul: Simul, host: User)(pairing: SimulPairing): Fu[(Game, shogi.Color)] =
    for {
      user <- userRepo byId pairing.player.user orFail s"No user with id ${pairing.player.user}"
      hostColor  = simul.hostColor
      senteUser  = hostColor.fold(host, user)
      goteUser   = hostColor.fold(user, host)
      clock      = simul.clock.chessClockOf(hostColor)
      perfPicker = lishogi.game.PerfPicker.mainOrDefault(shogi.Speed(clock.config), pairing.player.variant, none)
      g = shogi.Game(
        variantOption = Some {
          if (simul.position.isEmpty) pairing.player.variant
          else shogi.variant.FromPosition
        },
        fen = simul.position.map(_.fen)
      )
      game1 = Game.make(
        shogi = g.copy(clock = clock.start.some, startedAtTurn = g.turns),
        sentePlayer = lishogi.game.Player.make(shogi.Sente, senteUser.some, perfPicker),
        gotePlayer = lishogi.game.Player.make(shogi.Gote, goteUser.some, perfPicker),
        mode = shogi.Mode.Casual,
        source = lishogi.game.Source.Simul,
        notationImport = None
      )
      game2 =
        game1
          .withId(pairing.gameId)
          .withSimulId(simul.id)
          .start
      _ <-
        (gameRepo insertDenormalized game2) >>-
          onGameStart(game2.id) >>-
          socket.startGame(simul, game2)
    } yield game2 -> hostColor

  private def update(simul: Simul) =
    repo.update(simul) >>- socket.reload(simul.id) >>- publish()

  private def WithSimul(
      finding: Simul.ID => Fu[Option[Simul]],
      simulId: Simul.ID
  )(updating: Simul => Simul): Funit = {
    workQueue(simulId) {
      finding(simulId) flatMap {
        _ ?? { simul =>
          update(updating(simul))
        }
      }
    }
  }

  private object publish {
    private val siteMessage = SendToFlag("simul", Json.obj("t" -> "reload"))
    private val debouncer = system.actorOf(
      Props(
        new Debouncer(
          5 seconds,
          { (_: Debouncer.Nothing) =>
            Bus.publish(siteMessage, "sendToFlag")
            repo.allCreatedFeaturable foreach { simuls =>
              renderer.actor ? actorApi.SimulTable(simuls) map { case view: String =>
                Bus.publish(ReloadSimuls(view), "lobbySocket")
              }
            }
          }
        )
      )
    )
    def apply(): Unit = { debouncer ! Debouncer.Nothing }
  }
}
