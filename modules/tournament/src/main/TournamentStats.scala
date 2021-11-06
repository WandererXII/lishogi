package lishogi.tournament

import reactivemongo.api.bson.Macros
import scala.concurrent.duration._

import shogi.Color
import lishogi.db.dsl._

final class TournamentStatsApi(
    playerRepo: PlayerRepo,
    pairingRepo: PairingRepo,
    mongoCache: lishogi.memo.MongoCache.Api
)(implicit ec: scala.concurrent.ExecutionContext) {

  def apply(tournament: Tournament): Fu[Option[TournamentStats]] =
    tournament.isFinished ?? cache.get(tournament.id).dmap(some)

  implicit private val statsBSONHandler = Macros.handler[TournamentStats]

  private val cache = mongoCache[Tournament.ID, TournamentStats](
    64,
    "tournament:stats",
    60 days,
    identity
  ) { loader =>
    _.expireAfterAccess(10 minutes)
      .maximumSize(256)
      .buildAsyncFuture(loader(fetch))
  }

  private def fetch(tournamentId: Tournament.ID): Fu[TournamentStats] =
    for {
      rating   <- playerRepo.averageRating(tournamentId)
      rawStats <- pairingRepo.rawStats(tournamentId)
    } yield TournamentStats.readAggregation(rating)(rawStats)
}

case class TournamentStats(
    games: Int,
    moves: Int,
    senteWins: Int,
    goteWins: Int,
    draws: Int,
    berserks: Int,
    averageRating: Int
)

private object TournamentStats {

  private case class ColorStats(games: Int, moves: Int, b1: Int, b2: Int) {
    def berserks = b1 + b2
  }

  def readAggregation(rating: Int)(docs: List[Bdoc]): TournamentStats = {
    val colorStats: Map[Option[Color], ColorStats] = docs.view.map { doc =>
      doc.getAsOpt[Boolean]("_id").map(Color.apply) ->
        ColorStats(
          ~doc.int("games"),
          ~doc.int("moves"),
          ~doc.int("b1"),
          ~doc.int("b2")
        )
    }.toMap
    TournamentStats(
      games = colorStats.foldLeft(0)(_ + _._2.games),
      moves = colorStats.foldLeft(0)(_ + _._2.moves),
      senteWins = colorStats.get(Color.Sente.some).??(_.games),
      goteWins = colorStats.get(Color.Gote.some).??(_.games),
      draws = colorStats.get(none).??(_.games),
      berserks = colorStats.foldLeft(0)(_ + _._2.berserks),
      averageRating = rating
    )
  }
}
