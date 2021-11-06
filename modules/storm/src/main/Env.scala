package lishogi.storm

import com.softwaremill.macwire._
import play.api.Configuration

import lishogi.common.config._
import lishogi.user.UserRepo

@Module
final class Env(
    appConfig: Configuration,
    db: lishogi.db.Db,
    colls: lishogi.puzzle.PuzzleColls,
    cacheApi: lishogi.memo.CacheApi,
    userRepo: UserRepo
)(implicit
    ec: scala.concurrent.ExecutionContext
) {

  private lazy val dayColl = db(CollName("storm_day"))

  lazy val selector = wire[StormSelector]

  private val signSecret = appConfig.get[Secret]("storm.secret")

  lazy val sign = wire[StormSign]

  lazy val json = wire[StormJson]

  lazy val highApi = wire[StormHighApi]

  lazy val dayApi = wire[StormDayApi]

  val forms = StormForm

  lishogi.common.Bus.subscribeFuns(
    "gdprErase" -> { case lishogi.user.User.GDPRErase(user) =>
      dayApi.eraseAllFor(user)
    }
  )
}
