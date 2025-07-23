package lila.tv

import scala.concurrent.Promise
import scala.concurrent.duration._

import play.api.libs.json.Json

import akka.pattern.{ ask => actorAsk }

import lila.common.Bus
import lila.game.Game
import lila.hub.Trouper

final private[tv] class TvTrouper(
    renderer: lila.hub.actors.Renderer,
    lightUserApi: lila.user.LightUserApi,
    recentTvGames: lila.round.RecentTvGames,
    gameProxyRepo: lila.round.GameProxyRepo,
    rematches: lila.game.Rematches,
)(implicit ec: scala.concurrent.ExecutionContext)
    extends Trouper {

  import TvTrouper._

  Bus.subscribe(this, "startGame")

  private val channelTroupers: Map[Tv.Channel, ChannelTrouper] = Tv.Channel.all.map { c =>
    c -> new ChannelTrouper(c, onSelect = this.!, gameProxyRepo.game, rematches.getAcceptedId)
  }.toMap

  private var channelChampions = Map[Tv.Channel, Tv.Champion]()

  private def forward[A](channel: Tv.Channel, msg: Any) = {
    channelTroupers get channel foreach { _ ! msg }
  }

  protected val process: Trouper.Receive = {

    case GetGameId(channel, promise) =>
      forward(channel, ChannelTrouper.GetGameId(promise))

    case GetGameIdAndHistory(channel, promise) =>
      forward(channel, ChannelTrouper.GetGameIdAndHistory(promise))

    case GetGameIds(channel, max, promise) =>
      forward(channel, ChannelTrouper.GetGameIds(max, promise))

    case GetChampions(promise) => promise success Tv.Champions(channelChampions)

    case lila.game.actorApi.StartGame(g) =>
      if (g.hasClock)
        channelTroupers collect {
          case (chan, trouper) if chan.filter(g) => trouper
        } foreach (_ addCandidate g)

    case s @ TvTrouper.Select => channelTroupers.foreach(_._2 ! s)

    case Selected(channel, game) =>
      import lila.socket.Socket.makeMessage
      import cats.implicits._
      val player = game.players.sortBy { p =>
        if (!p.isHuman) {
          if (channel.key == "computer") (Int.MaxValue / 2) + ~p.rating
          else Int.MinValue
        } else ~p.rating
      }.lastOption | game.firstPlayer
      val user = player.userId flatMap lightUserApi.sync
      (user, player.rating) mapN { (u, r) =>
        channelChampions += (channel -> Tv.Champion(u, r, game.id))
      }
      recentTvGames.put(game)
      val data = Json.obj(
        "channel" -> channel.key,
        "id"      -> game.id,
        "color"   -> game.firstColor.name,
        "player" -> user.map { u =>
          Json.obj(
            "name"   -> u.name,
            "title"  -> u.title,
            "rating" -> player.rating,
          )
        },
      )
      Bus.publish(lila.hub.actorApi.tv.TvSelect(game.id, game.speed, data), "tvSelect")
      if (channel == Tv.Channel.Standard) {
        implicit def timeout = lila.tv.makeTimeout(100 millis)
        actorAsk(renderer.actor, actorApi.RenderFeaturedJs(game)) foreach { case html: String =>
          val event = lila.hub.actorApi.game.ChangeFeatured(
            game.id,
            makeMessage(
              "featured",
              Json.obj(
                "html"  -> html,
                "color" -> game.firstColor.name,
                "id"    -> game.id,
              ),
            ),
          )
          Bus.publish(event, "changeFeaturedGame")
        }
      }
  }
}

private[tv] object TvTrouper {

  case class GetGameId(channel: Tv.Channel, promise: Promise[Option[Game.ID]])
  case class GetGameIds(channel: Tv.Channel, max: Int, promise: Promise[List[Game.ID]])

  case class GetGameIdAndHistory(
      channel: Tv.Channel,
      promise: Promise[ChannelTrouper.GameIdAndHistory],
  )

  case object Select
  case class Selected(channel: Tv.Channel, game: Game)

  case class GetChampions(promise: Promise[Tv.Champions])
}
