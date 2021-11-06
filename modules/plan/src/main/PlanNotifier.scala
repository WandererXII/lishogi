package lishogi.plan

import akka.actor._
import scala.concurrent.duration._

import lishogi.hub.actorApi.timeline.{ Propagate }
import lishogi.notify.Notification.Notifies
import lishogi.notify.{ Notification, NotifyApi }
import lishogi.user.User

final private[plan] class PlanNotifier(
    notifyApi: NotifyApi,
    timeline: lishogi.hub.actors.Timeline
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: ActorSystem
) {

  def onStart(user: User) =
    fuccess {
      system.scheduler.scheduleOnce(5 seconds) {
        notifyApi.addNotification(
          Notification.make(
            Notifies(user.id),
            lishogi.notify.PlanStart(user.id)
          )
        )
      }
      val msg = Propagate(lishogi.hub.actorApi.timeline.PlanStart(user.id))
      timeline ! (msg toFollowersOf user.id)
    }

  def onExpire(user: User) =
    notifyApi.addNotification(
      Notification.make(
        Notifies(user.id),
        lishogi.notify.PlanExpire(user.id)
      )
    )
}
