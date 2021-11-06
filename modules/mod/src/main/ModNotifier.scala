package lishogi.mod

import lishogi.notify.{ Notification, NotifyApi }
import lishogi.report.{ Mod, Suspect, Victim }

final private class ModNotifier(
    notifyApi: NotifyApi,
    reportApi: lishogi.report.ReportApi
)(implicit ec: scala.concurrent.ExecutionContext) {

  def reporters(mod: Mod, sus: Suspect): Funit =
    reportApi.recentReportersOf(sus) flatMap {
      _.filter(r => mod.user.id != r.value)
        .map { reporterId =>
          notifyApi.addNotification(
            Notification.make(
              notifies = Notification.Notifies(reporterId.value),
              content = lishogi.notify.ReportedBanned
            )
          )
        }
        .sequenceFu
        .void
    }

  def refund(victim: Victim, pt: lishogi.rating.PerfType, points: Int): Funit =
    notifyApi.addNotification {
      implicit val lang = victim.user.realLang | lishogi.i18n.defaultLang
      Notification.make(
        notifies = Notification.Notifies(victim.user.id),
        content = lishogi.notify.RatingRefund(pt.trans, points)
      )
    }
}
