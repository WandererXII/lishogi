package lishogi.pool

import com.softwaremill.macwire._

import lishogi.common.Bus
import lishogi.game.Game

@Module
final class Env(
    userRepo: lishogi.user.UserRepo,
    gameRepo: lishogi.game.GameRepo,
    idGenerator: lishogi.game.IdGenerator,
    playbanApi: lishogi.playban.PlaybanApi
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: akka.actor.ActorSystem
) {

  private lazy val hookThieve = wire[HookThieve]

  private val onStart = (gameId: Game.Id) => Bus.publish(gameId, "gameStartId")

  private lazy val gameStarter = wire[GameStarter]

  lazy val api = wire[PoolApi]

  def poolConfigs = PoolList.all
}
