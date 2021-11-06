package lishogi.learn

import play.api.Configuration

import lishogi.common.config._

final class Env(
    appConfig: Configuration,
    db: lishogi.db.Db
)(implicit ec: scala.concurrent.ExecutionContext) {
  lazy val api = new LearnApi(
    coll = db(appConfig.get[CollName]("learn.collection.progress"))
  )
}
