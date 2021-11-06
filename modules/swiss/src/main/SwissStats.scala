package lishogi.swiss

import akka.stream.scaladsl._
import reactivemongo.api.bson.Macros
import scala.concurrent.duration._

import lishogi.db.dsl._

case class SwissStats(
    games: Int = 0,
    senteWins: Int = 0,
    goteWins: Int = 0,
    draws: Int = 0,
    byes: Int = 0,
    absences: Int = 0,
    averageRating: Int = 0
)

final class SwissStatsApi(
    colls: SwissColls,
    sheetApi: SwissSheetApi,
    mongoCache: lishogi.memo.MongoCache.Api
)(implicit
    ec: scala.concurrent.ExecutionContext,
    mat: akka.stream.Materializer
) {

  import BsonHandlers._

  def apply(swiss: Swiss): Fu[Option[SwissStats]] =
    swiss.isFinished ?? cache.get(swiss.id).dmap(some).dmap(_.filter(_.games > 0))

  implicit private val statsBSONHandler = Macros.handler[SwissStats]

  private val cache = mongoCache[Swiss.Id, SwissStats](
    64,
    "swiss:stats",
    60 days,
    _.value
  ) { loader =>
    _.expireAfterAccess(5 seconds)
      .maximumSize(256)
      .buildAsyncFuture(loader(fetch))
  }

  private def fetch(id: Swiss.Id): Fu[SwissStats] =
    colls.swiss.byId[Swiss](id.value) flatMap {
      _.filter(_.nbPlayers > 0).fold(fuccess(SwissStats())) { swiss =>
        sheetApi
          .source(swiss)
          .toMat(Sink.fold(SwissStats()) { case (stats, (player, pairings, sheet)) =>
            pairings.values.foldLeft((0, 0, 0, 0)) { case ((games, senteWins, goteWins, draws), pairing) =>
              (
                games + 1,
                senteWins + pairing.senteWins.??(1),
                goteWins + pairing.goteWins.??(1),
                draws + pairing.isDraw.??(1)
              )
            } match {
              case (games, senteWins, goteWins, draws) =>
                sheet.outcomes.foldLeft((0, 0)) { case ((byes, absences), outcome) =>
                  (
                    byes + (outcome == SwissSheet.Bye).??(1),
                    absences + (outcome == SwissSheet.Absent).??(1)
                  )
                } match {
                  case (byes, absences) =>
                    stats.copy(
                      games = stats.games + games,
                      senteWins = stats.senteWins + senteWins,
                      goteWins = stats.goteWins + goteWins,
                      draws = stats.draws + draws,
                      byes = stats.byes + byes,
                      absences = stats.absences + absences,
                      averageRating = stats.averageRating + player.rating
                    )
                }
            }
          })(Keep.right)
          .run()
          .dmap { s => s.copy(games = s.games / 2, averageRating = s.averageRating / swiss.nbPlayers) }
      }
    }
}
