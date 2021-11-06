package lishogi.bot

import com.softwaremill.macwire._

@Module
final class Env(
    chatApi: lishogi.chat.ChatApi,
    gameRepo: lishogi.game.GameRepo,
    lightUserApi: lishogi.user.LightUserApi,
    rematches: lishogi.game.Rematches,
    isOfferingRematch: lishogi.round.IsOfferingRematch
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: akka.actor.ActorSystem,
    mode: play.api.Mode
) {

  private def scheduler = system.scheduler

  lazy val jsonView = wire[BotJsonView]

  lazy val gameStateStream = wire[GameStateStream]

  lazy val player = wire[BotPlayer]

  lazy val onlineApiUsers: OnlineApiUsers = wire[OnlineApiUsers]

  val form = BotForm
}
