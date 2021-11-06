package lishogi.irwin

import akka.actor._
import com.softwaremill.macwire._
import scala.concurrent.duration._

import lishogi.common.config._
import lishogi.tournament.TournamentApi

final class Env(
    tournamentApi: TournamentApi,
    modApi: lishogi.mod.ModApi,
    reportApi: lishogi.report.ReportApi,
    notifyApi: lishogi.notify.NotifyApi,
    userCache: lishogi.user.Cached,
    gameRepo: lishogi.game.GameRepo,
    userRepo: lishogi.user.UserRepo,
    analysisRepo: lishogi.analyse.AnalysisRepo,
    settingStore: lishogi.memo.SettingStore.Builder,
    db: lishogi.db.Db
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: ActorSystem
) {

  private lazy val reportColl = db(CollName("irwin_report"))

  lazy val irwinThresholdsSetting = IrwinThresholds makeSetting settingStore

  lazy val stream = wire[IrwinStream]

  lazy val api = wire[IrwinApi]

  system.scheduler.scheduleWithFixedDelay(5 minutes, 5 minutes) { () =>
    tournamentApi.allCurrentLeadersInStandard flatMap api.requests.fromTournamentLeaders
  }
  system.scheduler.scheduleWithFixedDelay(15 minutes, 15 minutes) { () =>
    userCache.getTop50Online flatMap api.requests.fromLeaderboard
  }
}
