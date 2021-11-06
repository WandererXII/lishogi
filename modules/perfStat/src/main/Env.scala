package lishogi.perfStat

import akka.actor._
import com.softwaremill.macwire._
import play.api.Configuration

import lishogi.common.config._

final class Env(
    appConfig: Configuration,
    lightUser: lishogi.common.LightUser.GetterSync,
    gameRepo: lishogi.game.GameRepo,
    db: lishogi.db.Db
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: ActorSystem
) {

  lazy val storage = new PerfStatStorage(
    coll = db(appConfig.get[CollName]("perfStat.collection.perf_stat"))
  )

  lazy val indexer = wire[PerfStatIndexer]

  lazy val jsonView = wire[JsonView]

  def get(user: lishogi.user.User, perfType: lishogi.rating.PerfType): Fu[PerfStat] =
    storage.find(user.id, perfType) getOrElse indexer.userPerf(user, perfType)

  lishogi.common.Bus.subscribeFun("finishGame") {
    case lishogi.game.actorApi.FinishGame(game, _, _) if !game.aborted =>
      indexer addGame game addFailureEffect { e =>
        lishogi.log("perfStat").error(s"index game ${game.id}", e)
      }
  }
}
