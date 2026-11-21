package lila.coordinate

import com.softwaremill.macwire._

import lila.common.config.CollName

final class Env(
    db: lila.db.Db,
)(implicit ec: scala.concurrent.ExecutionContext) {

  private lazy val scoreColl = db(CollName("coordinate_score"))

  lazy val api = wire[CoordinateApi]

  lazy val forms = DataForm
}
