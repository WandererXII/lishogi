package lila.bot

import scala.concurrent.duration._

import play.api.i18n.Lang
import play.api.libs.json._

import akka.actor._
import akka.stream.scaladsl._

import lila.chat.Chat
import lila.chat.UserLine
import lila.common.Bus
import lila.game.Game
import lila.game.Pov
import lila.game.actorApi.AbortedBy
import lila.game.actorApi.FinishGame
import lila.game.actorApi.MoveGameEvent
import lila.hub.actorApi.map.Tell
import lila.round.actorApi.BotConnected
import lila.round.actorApi.round.QuietFlag

final class GameStateStream(
    onlineApiUsers: OnlineApiUsers,
    jsonView: BotJsonView,
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: ActorSystem,
) {

  private case object SetOnline
  private case class User(id: lila.user.User.ID, isBot: Boolean)

  private val blueprint =
    Source.queue[Option[JsObject]](32, akka.stream.OverflowStrategy.dropHead)

  def apply(game: Game, as: shogi.Color, u: lila.user.User)(implicit
      lang: Lang,
  ): Source[Option[JsObject], _] = {

    // kill previous one if any
    Bus.publish(PoisonPill, uniqChan(game pov as))

    blueprint mapMaterializedValue { queue =>
      val actor = system.actorOf(
        Props(mkActor(game, as, User(u.id, u.isBot), queue)),
        name = s"GameStateStream:${game.id}:${lila.common.ThreadLocalRandom nextString 8}",
      )
      queue.watchCompletion().foreach { _ =>
        actor ! PoisonPill
      }
    }
  }

  private def uniqChan(pov: Pov) = s"gameStreamFor:${pov.fullId}"

  private def mkActor(
      game: Game,
      as: shogi.Color,
      user: User,
      queue: SourceQueueWithComplete[Option[JsObject]],
  )(implicit lang: Lang) =
    new Actor {

      val id = game.id

      var gameOver = false

      private val classifiers = List(
        MoveGameEvent makeChan id,
        s"boardDrawOffer:${id}",
        s"boardTakeback:$id",
        "finishGame",
        "abortGame",
        uniqChan(game pov as),
        Chat chanOf Chat.Id(id),
      ) :::
        user.isBot.option(Chat chanOf Chat.Id(s"$id/w")).toList

      override def preStart(): Unit = {
        super.preStart()
        Bus.subscribe(self, classifiers)
        // prepend the full game JSON at the start of the stream
        queue offer (jsonView gameFull game).some
        // close stream if game is over
        if (game.finished) onGameOver(none)
        else self ! SetOnline
        lila.mon.bot.gameStream("start").increment()
        Bus.publish(Tell(game.id, BotConnected(as, true)), "roundSocket")
      }

      override def postStop(): Unit = {
        super.postStop()
        Bus.unsubscribe(self, classifiers)
        // hang around if game is over
        // so the opponent has a chance to rematch
        context.system.scheduler.scheduleOnce(if (gameOver) 10 second else 1 second) {
          Bus.publish(Tell(game.id, BotConnected(as, false)), "roundSocket")
        }
        queue.complete()
        lila.mon.bot.gameStream("stop").increment().unit
      }

      def receive = {
        case MoveGameEvent(g, _, _) if g.id == id => pushState(g).unit
        case lila.chat.actorApi.ChatLine(chatId, UserLine(username, _, text, false, false)) =>
          pushChatLine(username, text, chatId.value.sizeIs == Game.gameIdSize).unit
        case FinishGame(g, _, _) if g.id == id                  => onGameOver(g.some).unit
        case AbortedBy(pov) if pov.gameId == id                 => onGameOver(pov.game.some).unit
        case lila.game.actorApi.BoardDrawOffer(g) if g.id == id => pushState(g).unit
        case lila.game.actorApi.BoardTakeback(g) if g.id == id  => pushState(g).unit
        case SetOnline =>
          onlineApiUsers.setOnline(user.id)
          context.system.scheduler
            .scheduleOnce(6 second) {
              // gotta send a message to check if the client has disconnected
              queue offer None
              self ! SetOnline
              Bus.publish(Tell(id, QuietFlag), "roundSocket")
            }
            .unit
      }

      def pushState(g: Game): Funit =
        queue offer jsonView.gameState(g).some void

      def pushChatLine(username: String, text: String, player: Boolean): Funit =
        queue offer jsonView.chatLine(username, text, player).some void

      def onGameOver(g: Option[Game]) =
        g ?? pushState >>- {
          gameOver = true
          self ! PoisonPill
        }
    }
}
