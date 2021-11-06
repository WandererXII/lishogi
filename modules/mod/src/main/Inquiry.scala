package lishogi.mod

import lishogi.common.LightUser
import lishogi.report.{ Report, ReportApi }
import lishogi.user.{ Note, NoteApi, User, UserRepo }

case class Inquiry(
    mod: LightUser,
    report: Report,
    moreReports: List[Report],
    notes: List[Note],
    history: List[lishogi.mod.Modlog],
    user: User
) {

  def allReports = report :: moreReports
}

final class InquiryApi(
    userRepo: UserRepo,
    reportApi: ReportApi,
    noteApi: NoteApi,
    logApi: ModlogApi
) {

  def forMod(mod: User)(implicit ec: scala.concurrent.ExecutionContext): Fu[Option[Inquiry]] =
    lishogi.security.Granter(_.SeeReport)(mod).?? {
      reportApi.inquiries.ofModId(mod.id).flatMap {
        _ ?? { report =>
          reportApi.moreLike(report, 10) zip
            userRepo.named(report.user) zip
            noteApi.forMod(report.user) zip
            logApi.userHistory(report.user) map { case moreReports ~ userOption ~ notes ~ history =>
              userOption ?? { user =>
                Inquiry(mod.light, report, moreReports, notes, history, user).some
              }
            }
        }
      }
    }
}
