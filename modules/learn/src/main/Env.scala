package lila.learn

import lila.common.config._

final class Env(
    db: lila.db.Db,
)(implicit ec: scala.concurrent.ExecutionContext) {
  lazy val api = new LearnApi(
    coll = db(CollName("learn_progress")),
  )
}
