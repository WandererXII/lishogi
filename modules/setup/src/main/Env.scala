package lishogi.setup

import com.softwaremill.macwire._
import play.api.Configuration

import lishogi.common.config._

@Module
final class Env(
    appConfig: Configuration,
    gameRepo: lishogi.game.GameRepo,
    fishnetPlayer: lishogi.fishnet.Player,
    onStart: lishogi.round.OnStart,
    gameCache: lishogi.game.Cached
)(implicit ec: scala.concurrent.ExecutionContext) {

  private lazy val maxPlaying = appConfig.get[Max]("setup.max_playing")

  lazy val forms = wire[FormFactory]

  lazy val processor = wire[Processor]
}
