package lishogi.insight

import com.softwaremill.macwire._
import play.api.Configuration

import lishogi.common.config._

@Module
final class Env(
    appConfig: Configuration,
    gameRepo: lishogi.game.GameRepo,
    userRepo: lishogi.user.UserRepo,
    analysisRepo: lishogi.analyse.AnalysisRepo,
    prefApi: lishogi.pref.PrefApi,
    relationApi: lishogi.relation.RelationApi,
    mongo: lishogi.db.Env
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: akka.actor.ActorSystem
) {

  private lazy val db = mongo.asyncDb(
    "insight",
    appConfig.get[String]("insight.mongodb.uri")
  )

  lazy val share = wire[Share]

  lazy val jsonView = wire[JsonView]

  private lazy val storage = new Storage(db(CollName("insight")))

  private lazy val aggregationPipeline = wire[AggregationPipeline]

  private lazy val povToEntry = wire[PovToEntry]

  private lazy val indexer = wire[Indexer]

  private lazy val userCacheApi = new UserCacheApi(db(CollName("insight_user_cache")))

  lazy val api = wire[InsightApi]

  lishogi.common.Bus.subscribeFun("analysisReady", "cheatReport") {
    case lishogi.analyse.actorApi.AnalysisReady(game, _)        => api updateGame game
    case lishogi.hub.actorApi.report.CheatReportCreated(userId) => api ensureLatest userId
  }
}
