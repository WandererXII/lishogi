package lishogi.bot

import scala.concurrent.duration._

import lishogi.common.Bus
import lishogi.hub.actorApi.socket.ApiUserIsOnline
import lishogi.memo.ExpireCallbackMemo

final class OnlineApiUsers(
    scheduler: akka.actor.Scheduler
)(implicit ec: scala.concurrent.ExecutionContext, mode: play.api.Mode) {

  private val cache = new ExpireCallbackMemo(
    10.seconds,
    userId => publish(userId, false)
  )

  def setOnline(userId: lishogi.user.User.ID): Unit = {
    // We must delay the event publication, because caffeine
    // delays the removal listener, therefore when a bot reconnects,
    // the offline event is sent after the online event.
    if (!cache.get(userId)) scheduler.scheduleOnce(1 second) { publish(userId, true) }
    cache.put(userId)
  }

  def setOffline(userId: lishogi.user.User.ID): Unit = {
    cache.remove(userId)
  }

  def get: Set[lishogi.user.User.ID] = cache.keySet

  private def publish(userId: lishogi.user.User.ID, isOnline: Boolean) =
    Bus.publish(ApiUserIsOnline(userId, isOnline), "onlineApiUsers")
}
