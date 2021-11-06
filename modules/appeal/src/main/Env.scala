package lishogi.appeal

import com.softwaremill.macwire._

import lishogi.common.config._

@Module
final class Env(
    db: lishogi.db.Db,
    userRepo: lishogi.user.UserRepo
)(implicit ec: scala.concurrent.ExecutionContext) {

  private val coll = db(CollName("appeal"))

  lazy val forms = wire[AppealForm]

  lazy val api: AppealApi = wire[AppealApi]
}
