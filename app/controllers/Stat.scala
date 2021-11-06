package controllers

import lishogi.app._
import views._

final class Stat(env: Env) extends LishogiController(env) {

  def ratingDistribution(perfKey: lishogi.rating.Perf.Key) =
    Open { implicit ctx =>
      lishogi.rating.PerfType(perfKey).filter(lishogi.rating.PerfType.leaderboardable.has) match {
        case Some(perfType) =>
          env.user.rankingApi.weeklyRatingDistribution(perfType) dmap { data =>
            Ok(html.stat.ratingDistribution(perfType, data))
          }
        case _ => notFound
      }
    }
}
