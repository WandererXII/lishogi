package lishogi.msg

import akka.actor.Cancellable
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.duration._

import lishogi.db.dsl._
import lishogi.notify.{ Notification, PrivateMessage }
import lishogi.common.String.shorten
import lishogi.user.User

final private class MsgNotify(
    colls: MsgColls,
    notifyApi: lishogi.notify.NotifyApi
)(implicit
    ec: scala.concurrent.ExecutionContext,
    scheduler: akka.actor.Scheduler
) {

  import BsonHandlers._

  private val delay = 5 seconds

  private val delayed = new ConcurrentHashMap[MsgThread.Id, Cancellable](256)

  def onPost(threadId: MsgThread.Id): Unit = schedule(threadId)

  def onRead(threadId: MsgThread.Id, userId: User.ID, contactId: User.ID): Funit = {
    !cancel(threadId) ??
      notifyApi
        .markRead(
          lishogi.notify.Notification.Notifies(userId),
          $doc(
            "content.type" -> "privateMessage",
            "content.user" -> contactId
          )
        )
        .void
  }

  def deleteAllBy(threads: List[MsgThread], user: User): Funit =
    threads
      .map { thread =>
        cancel(thread.id)
        notifyApi
          .remove(
            lishogi.notify.Notification.Notifies(thread other user),
            $doc("content.user" -> user.id)
          )
          .void
      }
      .sequenceFu
      .void

  private def schedule(threadId: MsgThread.Id): Unit =
    delayed.compute(
      threadId,
      (id, canc) => {
        Option(canc).foreach(_.cancel())
        scheduler.scheduleOnce(delay) {
          delayed remove id
          doNotify(threadId)
        }
      }
    )

  private def cancel(threadId: MsgThread.Id): Boolean =
    Option(delayed remove threadId).map(_.cancel()).isDefined

  private def doNotify(threadId: MsgThread.Id): Funit =
    colls.thread.byId[MsgThread](threadId.value) flatMap {
      _ ?? { thread =>
        val msg  = thread.lastMsg
        val dest = thread other msg.user
        !thread.delBy(dest) ?? {
          lishogi.common.Bus.publish(MsgThread.Unread(thread), "msgUnread")
          notifyApi addNotification Notification.make(
            Notification.Notifies(dest),
            PrivateMessage(
              PrivateMessage.Sender(msg.user),
              PrivateMessage.Text(shorten(msg.text, 80))
            )
          )
        }
      }
    }
}
