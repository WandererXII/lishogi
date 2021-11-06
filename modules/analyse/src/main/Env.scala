package lishogi.analyse

import com.softwaremill.macwire._

import lishogi.common.config._

@Module
final class Env(
    db: lishogi.db.Db,
    gameRepo: lishogi.game.GameRepo
)(implicit ec: scala.concurrent.ExecutionContext) {

  lazy val analysisRepo = new AnalysisRepo(db(CollName("analysis2")))

  lazy val requesterApi = new RequesterApi(db(CollName("analysis_requester")))

  lazy val analyser = wire[Analyser]

  lazy val annotator = new Annotator
}
