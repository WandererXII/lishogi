package lila.round

import org.goochjs.glicko2._

import shogi.Color

import lila.game.Game
import lila.game.GameRepo
import lila.game.Provisionals
import lila.game.RatingDiffs
import lila.history.HistoryApi
import lila.rating.Glicko
import lila.rating.Perf
import lila.rating.RatingFactors
import lila.rating.RatingRegulator
import lila.rating.{ PerfType => PT }
import lila.user.Perfs
import lila.user.RankingApi
import lila.user.User
import lila.user.UserRepo

final class PerfsUpdater(
    gameRepo: GameRepo,
    userRepo: UserRepo,
    historyApi: HistoryApi,
    rankingApi: RankingApi,
    botFarming: BotFarming,
    ratingFactors: () => RatingFactors,
)(implicit ec: scala.concurrent.ExecutionContext) {

  def save(game: Game, sente: User, gote: User): Fu[Option[(RatingDiffs, Provisionals)]] =
    botFarming(game) flatMap {
      case true => fuccess(none)
      case _ =>
        (game.rated && game.finished && game.accountable && !sente.lame && !gote.lame) ?? {
          val perfType     = game.perfType
          val ratingsSente = mkRatings(sente.perfs)
          val ratingsGote  = mkRatings(gote.perfs)
          val result       = resultOf(game)
          perfType match {
            case PT.Dobutsu =>
              updateRatings(ratingsSente.dobutsu, ratingsGote.dobutsu, result)
            case PT.Checkshogi =>
              updateRatings(ratingsSente.checkshogi, ratingsGote.checkshogi, result)
            case PT.Kyotoshogi =>
              updateRatings(ratingsSente.kyotoshogi, ratingsGote.kyotoshogi, result)
            case PT.Annanshogi =>
              updateRatings(ratingsSente.annanshogi, ratingsGote.annanshogi, result)
            case PT.Chushogi =>
              updateRatings(ratingsSente.chushogi, ratingsGote.chushogi, result)
            case PT.Minishogi =>
              updateRatings(ratingsSente.minishogi, ratingsGote.minishogi, result)
            case PT.Correspondence =>
              updateRatings(ratingsSente.correspondence, ratingsGote.correspondence, result)
            case PT.RealTime =>
              updateRatings(ratingsSente.realTime, ratingsGote.realTime, result)
            case _ =>
          }
          val perfsSente                          = mkPerfs(ratingsSente, sente -> gote, game)
          val perfsGote                           = mkPerfs(ratingsGote, gote -> sente, game)
          def provisionalRatingLens(perfs: Perfs) = perfs(perfType).glicko.provisional
          def intRatingLens(perfs: Perfs)         = perfs(perfType).glicko.intRating
          val ratingDiffs = Color.Map(
            intRatingLens(perfsSente) - intRatingLens(sente.perfs),
            intRatingLens(perfsGote) - intRatingLens(gote.perfs),
          )
          val provisionals = Color.Map(
            provisionalRatingLens(perfsSente),
            provisionalRatingLens(perfsGote),
          )
          gameRepo.setRatingDiffs(game.id, ratingDiffs) zip
            userRepo.setPerfs(sente, perfsSente, sente.perfs) zip
            userRepo.setPerfs(gote, perfsGote, gote.perfs) zip
            historyApi.add(sente, game, perfsSente) zip
            historyApi.add(gote, game, perfsGote) zip
            rankingApi.save(sente, perfType, perfsSente(perfType)) zip
            rankingApi.save(gote, perfType, perfsGote(perfType)) inject (
              ratingDiffs,
              provisionals,
            ).some
        }
    }

  private case class Ratings(
      minishogi: Rating,
      chushogi: Rating,
      annanshogi: Rating,
      kyotoshogi: Rating,
      checkshogi: Rating,
      dobutsu: Rating,
      realTime: Rating,
      correspondence: Rating,
  )

  private def mkRatings(perfs: Perfs) =
    Ratings(
      minishogi = perfs.minishogi.toRating,
      chushogi = perfs.chushogi.toRating,
      annanshogi = perfs.annanshogi.toRating,
      kyotoshogi = perfs.kyotoshogi.toRating,
      checkshogi = perfs.checkshogi.toRating,
      dobutsu = perfs.dobutsu.toRating,
      realTime = perfs.realTime.toRating,
      correspondence = perfs.correspondence.toRating,
    )

  private def resultOf(game: Game): Glicko.Result =
    game.winnerColor match {
      case Some(shogi.Sente) => Glicko.Result.Win
      case Some(shogi.Gote)  => Glicko.Result.Loss
      case None              => Glicko.Result.Draw
    }

  private def updateRatings(sente: Rating, gote: Rating, result: Glicko.Result): Unit =
    if (result != Glicko.Result.Draw) {
      val results = new RatingPeriodResults()
      result match {
        case Glicko.Result.Win  => results.addResult(sente, gote)
        case Glicko.Result.Loss => results.addResult(gote, sente)
        case _                  =>
      }
      try {
        Glicko.system.updateRatings(results, true)
      } catch {
        case e: Exception => logger.error("update ratings", e)
      }
    }

  private def mkPerfs(ratings: Ratings, users: (User, User), game: Game): Perfs =
    users match {
      case (player, opponent) =>
        val perfs            = player.perfs
        val isStd            = game.variant.standard
        val isHumanVsMachine = player.noBot && opponent.isBot
        def addRatingIf(cond: Boolean, perf: Perf, rating: Rating) =
          if (cond) {
            val p = perf.addOrReset(_.round.error.glicko, s"game ${game.id}")(rating, game.movedAt)
            if (isHumanVsMachine) p averageGlicko perf // halve rating diffs for human
            else p
          } else perf
        val perfs1 = perfs.copy(
          minishogi = addRatingIf(game.variant.minishogi, perfs.minishogi, ratings.minishogi),
          chushogi = addRatingIf(game.variant.chushogi, perfs.chushogi, ratings.chushogi),
          annanshogi = addRatingIf(game.variant.annanshogi, perfs.annanshogi, ratings.annanshogi),
          kyotoshogi = addRatingIf(game.variant.kyotoshogi, perfs.kyotoshogi, ratings.kyotoshogi),
          checkshogi = addRatingIf(game.variant.checkshogi, perfs.checkshogi, ratings.checkshogi),
          dobutsu = addRatingIf(game.variant.dobutsu, perfs.dobutsu, ratings.dobutsu),
          realTime = addRatingIf(isStd && game.hasClock, perfs.realTime, ratings.realTime),
          correspondence = addRatingIf(
            isStd && game.isCorrespondence,
            perfs.correspondence,
            ratings.correspondence,
          ),
        )
        val r = RatingRegulator(ratingFactors()) _
        perfs1.copy(
          minishogi = r(PT.Minishogi, perfs.minishogi, perfs1.minishogi),
          chushogi = r(PT.Chushogi, perfs.chushogi, perfs1.chushogi),
          annanshogi = r(PT.Annanshogi, perfs.annanshogi, perfs1.annanshogi),
          kyotoshogi = r(PT.Kyotoshogi, perfs.kyotoshogi, perfs1.kyotoshogi),
          checkshogi = r(PT.Checkshogi, perfs.checkshogi, perfs1.checkshogi),
          dobutsu = r(PT.Dobutsu, perfs.dobutsu, perfs1.dobutsu),
          realTime = r(PT.RealTime, perfs.realTime, perfs1.realTime),
          correspondence = r(PT.Correspondence, perfs.correspondence, perfs1.correspondence),
        )
    }
}
