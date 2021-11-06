package lishogi.clas

import com.softwaremill.macwire._

import lishogi.common.config._

@Module
final class Env(
    db: lishogi.db.Db,
    userRepo: lishogi.user.UserRepo,
    gameRepo: lishogi.game.GameRepo,
    historyApi: lishogi.history.HistoryApi,
    puzzleColls: lishogi.puzzle.PuzzleColls,
    msgApi: lishogi.msg.MsgApi,
    lightUserAsync: lishogi.common.LightUser.Getter,
    securityForms: lishogi.security.DataForm,
    authenticator: lishogi.user.Authenticator,
    cacheApi: lishogi.memo.CacheApi,
    baseUrl: BaseUrl
)(implicit ec: scala.concurrent.ExecutionContext) {

  lazy val nameGenerator = wire[NameGenerator]

  lazy val forms = wire[ClasForm]

  private val colls = wire[ClasColls]

  lazy val api: ClasApi = wire[ClasApi]

  private def getStudentIds = () => api.student.allIds

  lazy val progressApi = wire[ClasProgressApi]

  lazy val markup = wire[ClasMarkup]

  lishogi.common.Bus.subscribeFuns(
    "finishGame" -> { case lishogi.game.actorApi.FinishGame(game, _, _) =>
      progressApi.onFinishGame(game)
    },
    "clas" -> { case lishogi.hub.actorApi.clas.IsTeacherOf(teacher, student, promise) =>
      promise completeWith api.clas.isTeacherOfStudent(teacher, Student.Id(student))
    }
  )
}

private class ClasColls(db: lishogi.db.Db) {
  val clas    = db(CollName("clas_clas"))
  val student = db(CollName("clas_student"))
  val invite  = db(CollName("clas_invite"))
}
