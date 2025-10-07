package lila.report

import scala.concurrent.duration._

import org.joda.time.DateTime

import lila.game.Game
import lila.game.GameRepo

final class AutoAnalysis(
    gameRepo: GameRepo,
    shoginet: lila.hub.actors.Shoginet,
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: akka.actor.ActorSystem,
) {

  def apply(candidate: Report.Candidate): Funit =
    if (candidate.isCheat) doItNow(candidate)
    else if (candidate.isPrint) fuccess {
      List(30, 90) foreach { minutes =>
        system.scheduler.scheduleOnce(minutes minutes) { doItNow(candidate).unit }
      }
    }
    else funit

  private def doItNow(candidate: Report.Candidate) =
    gamesToAnalyse(candidate) map { games =>
      if (games.nonEmpty)
        logger.info(
          s"Auto-analyse ${games.size} games after report by ${candidate.reporter.user.id}",
        )
      games foreach { game =>
        lila.mon.cheat.autoAnalysis("Report").increment()
        shoginet ! lila.hub.actorApi.shoginet.AutoAnalyse(game.id)
      }
    }

  private def gamesToAnalyse(candidate: Report.Candidate): Fu[List[Game]] = {
    gameRepo.recentAnalysableGamesByUserId(candidate.suspect.user.id, 20) flatMap { as =>
      gameRepo.lastGamesBetween(
        candidate.suspect.user,
        candidate.reporter.user,
        DateTime.now.minusHours(2),
        10,
      ) dmap { as ++ _ }
    }
  }.map {
    _.filter { g =>
      g.analysable && !g.metadata.analysed
    }.distinct
      .sortBy(-_.createdAt.getSeconds)
      .take(10)
  }
}
