package lishogi.evalCache

import com.softwaremill.macwire._
import play.api.Configuration

import lishogi.common.Bus
import lishogi.common.config.CollName
import lishogi.hub.actorApi.socket.remote.{ TellSriIn, TellSriOut }
import lishogi.socket.Socket.Sri

@Module
final class Env(
    appConfig: Configuration,
    userRepo: lishogi.user.UserRepo,
    db: lishogi.db.Db,
    cacheApi: lishogi.memo.CacheApi,
    scheduler: akka.actor.Scheduler
)(implicit
    ec: scala.concurrent.ExecutionContext,
    mode: play.api.Mode
) {

  private lazy val coll = db(appConfig.get[CollName]("evalCache.collection.evalCache"))

  private lazy val truster = wire[EvalCacheTruster]

  private lazy val upgrade = wire[EvalCacheUpgrade]

  lazy val api: EvalCacheApi = wire[EvalCacheApi]

  private lazy val socketHandler = wire[EvalCacheSocketHandler]

  // remote socket support
  Bus.subscribeFun("remoteSocketIn:evalGet") { case TellSriIn(sri, _, msg) =>
    msg obj "d" foreach { d =>
      // TODO send once, let lila-ws distribute
      socketHandler.evalGet(Sri(sri), d, res => Bus.publish(TellSriOut(sri, res), "remoteSocketOut"))
    }
  }
  Bus.subscribeFun("remoteSocketIn:evalPut") { case TellSriIn(sri, Some(userId), msg) =>
    msg obj "d" foreach { d =>
      socketHandler.untrustedEvalPut(Sri(sri), userId, d)
    }
  }
  // END remote socket support

  def cli =
    new lishogi.common.Cli {
      def process = { case "eval-cache" :: "drop" :: fenParts =>
        api.drop(shogi.variant.Standard, shogi.format.FEN(fenParts mkString " ")) inject "done!"
      }
    }
}
