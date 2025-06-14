package lila.round

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

import akka.actor._
import akka.stream.scaladsl._
import org.joda.time.DateTime

import lila.common.LilaStream
import lila.db.dsl._
import lila.game.Game
import lila.game.GameRepo
import lila.game.Query
import lila.notify.Notification
import lila.notify.NotifyApi
import lila.notify.PausedGame
import lila.round.actorApi.round.Abandon
import lila.round.actorApi.round.QuietFlag

/*
 * Cleans up unfinished games
 * and flagged games when no one is around
 */
final private[round] class Titivate(
    tellRound: TellRound,
    gameRepo: GameRepo,
    bookmark: lila.hub.actors.Bookmark,
    chatApi: lila.chat.ChatApi,
    notifyApi: NotifyApi,
)(implicit mat: akka.stream.Materializer)
    extends Actor {

  private type GameOrFail = Either[(Game.ID, Throwable), Game]

  object Run

  override def preStart(): Unit = {
    scheduleNext()
    context setReceiveTimeout 30.seconds
  }

  implicit def ec: ExecutionContextExecutor = context.system.dispatcher
  def scheduler                             = context.system.scheduler

  def scheduleNext(): Unit = scheduler.scheduleOnce(10 seconds, self, Run).unit

  def receive = {
    case ReceiveTimeout =>
      val msg = "Titivate timed out!"
      logBranch.error(msg)
      throw new RuntimeException(msg)

    case Run =>
      gameRepo.count(_.checkable) foreach { total =>
        lila.mon.round.titivate.total.record(total)
        gameRepo
          .docCursor(Query.checkable)
          .documentSource(100)
          .via(gameRead)
          .via(gameFlow)
          .toMat(LilaStream.sinkCount)(Keep.right)
          .run()
          .addEffect(lila.mon.round.titivate.game.record(_).unit)
          .>> {
            gameRepo
              .count(_.checkableOld)
              .dmap(lila.mon.round.titivate.old.record(_))
          }
          .monSuccess(_.round.titivate.time)
          .logFailure(logBranch)
          .addEffectAnyway(scheduleNext().unit)
      }
  }

  private val logBranch = logger branch "titivate"

  private val gameRead = Flow[Bdoc].map { doc =>
    lila.game.BSONHandlers.gameBSONHandler
      .readDocument(doc)
      .fold[GameOrFail](
        err => Left(~doc.string("_id") -> err),
        Right.apply,
      )
  }

  private val gameFlow: Flow[GameOrFail, Unit, _] = Flow[GameOrFail].mapAsyncUnordered(8) {

    case Left((id, err)) =>
      lila.mon.round.titivate.broken(err.getClass.getSimpleName).increment()
      logBranch.warn(s"Can't read game $id", err)
      gameRepo unsetCheckAt id

    case Right(game) =>
      game match {

        case game if game.paused =>
          gameRepo.unsetCheckAt(game.id) >> notifyApi.addNotifications(
            game.userIds map { userId =>
              Notification.make(
                Notification.Notifies(userId),
                PausedGame(
                  PausedGame.GameId(game.id),
                  game.userIds.find(_ != userId).map(PausedGame.OpponentId),
                ),
              )
            },
          )

        case game if game.finished || game.isNotationImport || game.playedThenAborted =>
          gameRepo unsetCheckAt game.id

        case game if game.outoftime(withGrace = true) =>
          fuccess {
            tellRound(game.id, QuietFlag)
          }

        case game if game.abandoned =>
          fuccess {
            tellRound(game.id, Abandon)
          }

        case game if game.unplayed =>
          bookmark ! lila.hub.actorApi.bookmark.Remove(game.id)
          chatApi.remove(lila.chat.Chat.Id(game.id))
          gameRepo.remove(game.id)

        case game =>
          game.clock match {

            case Some(clock) if clock.isRunning =>
              val minutes = clock.estimateTotalSeconds / 60
              gameRepo.setCheckAt(game, DateTime.now plusMinutes minutes).void

            case Some(_) =>
              val hours = Game.unplayedHours
              gameRepo.setCheckAt(game, DateTime.now plusHours hours).void

            case None =>
              val hours = game.daysPerTurn.fold(
                if (game.hasAi) Game.aiAbandonedHours
                else Game.abandonedDays * 24,
              )(_ * 24)
              gameRepo.setCheckAt(game, DateTime.now plusHours hours).void
          }
      }
  }
}
