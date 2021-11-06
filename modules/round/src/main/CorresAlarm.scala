package lishogi.round

import akka.stream.scaladsl._
import org.joda.time.DateTime
import reactivemongo.akkastream.cursorProducer

import scala.concurrent.duration._

import lishogi.common.Bus
import lishogi.common.LishogiStream
import lishogi.db.dsl._
import lishogi.game.{ Game, Pov }

final private class CorresAlarm(
    coll: Coll,
    hasUserId: (Game, lishogi.user.User.ID) => Fu[Boolean],
    proxyGame: Game.ID => Fu[Option[Game]]
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: akka.actor.ActorSystem
) {

  private case class Alarm(
      _id: String,       // game id
      ringsAt: DateTime, // when to notify the player
      expiresAt: DateTime
  )

  implicit private val AlarmHandler = reactivemongo.api.bson.Macros.handler[Alarm]

  private def scheduleNext(): Unit = system.scheduler.scheduleOnce(10 seconds) { run() }

  system.scheduler.scheduleOnce(10 seconds) { scheduleNext() }

  Bus.subscribeFun("finishGame") { case lishogi.game.actorApi.FinishGame(game, _, _) =>
    if (game.hasCorrespondenceClock && !game.hasAi) coll.delete.one($id(game.id))
  }

  Bus.subscribeFun("moveEventCorres") {
    case lishogi.hub.actorApi.round.CorresMoveEvent(move, _, _, alarmable, _) if alarmable =>
      proxyGame(move.gameId) flatMap {
        _ ?? { game =>
          game.bothPlayersHaveMoved ?? {
            game.playableCorrespondenceClock ?? { clock =>
              val remainingTime = clock remainingTime game.turnColor
              val ringsAt       = DateTime.now.plusSeconds(remainingTime.toInt * 8 / 10)
              coll.update
                .one(
                  $id(game.id),
                  Alarm(
                    _id = game.id,
                    ringsAt = ringsAt,
                    expiresAt = DateTime.now.plusSeconds(remainingTime.toInt * 2)
                  ),
                  upsert = true
                )
                .void
            }
          }
        }
      }
  }

  private def run(): Unit =
    coll.ext
      .find($doc("ringsAt" $lt DateTime.now))
      .cursor[Alarm]()
      .documentSource(200)
      .mapAsyncUnordered(4)(alarm => proxyGame(alarm._id))
      .via(LishogiStream.collect)
      .mapAsyncUnordered(4) { game =>
        val pov = Pov(game, game.turnColor)
        pov.player.userId.fold(fuccess(true))(u => hasUserId(pov.game, u)).addEffect {
          case true  => // already looking at the game
          case false => Bus.publish(lishogi.game.actorApi.CorresAlarmEvent(pov), "corresAlarm")
        } >> coll.delete.one($id(game.id))
      }
      .toMat(LishogiStream.sinkCount)(Keep.right)
      .run()
      .mon(_.round.alarm.time)
      .addEffectAnyway { scheduleNext() }
}
