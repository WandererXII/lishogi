package lishogi.pool

import scala.concurrent.Promise

import lishogi.common.Bus

final private class HookThieve()(implicit
    ec: scala.concurrent.ExecutionContext,
    system: akka.actor.ActorSystem
) {

  import HookThieve._

  def candidates(clock: shogi.Clock.Config): Fu[PoolHooks] =
    Bus
      .ask[PoolHooks]("lobbyTrouper")(GetCandidates(clock, _))
      .logFailure(logger)
      .nevermind(PoolHooks(Vector.empty))

  def stolen(poolHooks: Vector[PoolHook], monId: String) = {
    lishogi.mon.lobby.pool.thieve.stolen(monId).record(poolHooks.size)
    if (poolHooks.nonEmpty) Bus.publish(StolenHookIds(poolHooks.map(_.hookId)), "lobbyTrouper")
  }
}

object HookThieve {

  case class GetCandidates(clock: shogi.Clock.Config, promise: Promise[PoolHooks])
  case class StolenHookIds(ids: Vector[String])

  case class PoolHook(hookId: String, member: PoolMember) {
    def is(m: PoolMember) = member.userId == m.userId
  }

  case class PoolHooks(hooks: Vector[PoolHook])
}
