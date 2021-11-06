package lishogi.tv

import akka.actor.ActorSystem
import com.softwaremill.macwire._
import scala.concurrent.duration._

@Module
final class Env(
    gameRepo: lishogi.game.GameRepo,
    renderer: lishogi.hub.actors.Renderer,
    lightUser: lishogi.common.LightUser.GetterSync,
    gameProxyRepo: lishogi.round.GameProxyRepo,
    system: ActorSystem,
    recentTvGames: lishogi.round.RecentTvGames,
    rematches: lishogi.game.Rematches
)(implicit ec: scala.concurrent.ExecutionContext) {

  private val tvTrouper = wire[TvTrouper]

  lazy val tv = wire[Tv]

  system.scheduler.scheduleWithFixedDelay(12 seconds, 3 seconds) { () =>
    tvTrouper ! TvTrouper.Select
  }
}
