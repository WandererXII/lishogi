package lishogi.coordinate

import play.api.Configuration
import com.softwaremill.macwire._

import lishogi.common.config.CollName

final class Env(
    appConfig: Configuration,
    db: lishogi.db.Db
)(implicit ec: scala.concurrent.ExecutionContext) {

  private lazy val scoreColl = db(appConfig.get[CollName]("coordinate.collection.score"))

  lazy val api = wire[CoordinateApi]

  lazy val forms = DataForm
}
