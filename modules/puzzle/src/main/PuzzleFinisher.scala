package lila.puzzle

import scala.concurrent.duration._
import scala.util.chaining._

import cats.implicits._
import org.goochjs.glicko2.Rating
import org.goochjs.glicko2.RatingCalculator
import org.goochjs.glicko2.RatingPeriodResults
import org.joda.time.DateTime

import lila.common.Bus
import lila.db.dsl._
import lila.rating.Glicko
import lila.rating.Perf
import lila.rating.PerfType
import lila.user.User
import lila.user.UserRepo

final private[puzzle] class PuzzleFinisher(
    api: PuzzleApi,
    userRepo: UserRepo,
    historyApi: lila.history.HistoryApi,
    colls: PuzzleColls,
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: akka.actor.ActorSystem,
) {

  import BsonHandlers._

  private val sequencer =
    new lila.hub.DuctSequencers(
      maxSize = 64,
      expiration = 5 minutes,
      timeout = 5 seconds,
      name = "puzzle.finish",
    )

  def batch(
      user: User,
      solutions: List[PuzzleForm.mobile.Solution],
  ): Fu[List[(PuzzleRound, IntRatingDiff)]] =
    solutions.foldM((user.perfs.puzzle, List.empty[(PuzzleRound, IntRatingDiff)])) {
      case ((perf, rounds), sol) =>
        apply(Puzzle.Id(sol.id), PuzzleTheme.findOrAny(sol.theme).key, user, Result(sol.win)) map {
          case Some((round, newPerf)) => {
            val rDiff = IntRatingDiff(newPerf.intRating - perf.intRating)
            (newPerf, (round, rDiff) :: rounds)
          }
          case None => (perf, rounds)
        }
    } map { case (_, rounds) => rounds.reverse }

  def apply(
      id: Puzzle.Id,
      theme: PuzzleTheme.Key,
      user: User,
      result: Result,
  ): Fu[Option[(PuzzleRound, Perf)]] =
    if (api.casual(user, id)) fuccess {
      PuzzleRound(
        id = PuzzleRound.Id(user.id, id),
        win = result.win,
        fixedAt = none,
        date = DateTime.now,
      ) -> user.perfs.puzzle
    } dmap some
    else
      sequencer(id.value) {
        api.round.find(user, id) flatMap { prevRound =>
          api.puzzle.find(id) flatMap {
            _ ?? { puzzle =>
              val now              = DateTime.now
              val formerUserRating = user.perfs.puzzle.intRating

              val (round, newPuzzleGlicko, userPerf) = prevRound match {
                case Some(prev) =>
                  (
                    prev.updateWithWin(result.win),
                    none,
                    user.perfs.puzzle,
                  )
                case None =>
                  val userRating = user.perfs.puzzle.toRating
                  val puzzleRating = new Rating(
                    puzzle.glicko.rating atLeast Glicko.minRating,
                    puzzle.glicko.deviation,
                    puzzle.glicko.volatility,
                    puzzle.plays,
                    null,
                  )
                  updateRatings(userRating, puzzleRating, result.glicko)
                  val newPuzzleGlicko = ponder
                    .puzzle(
                      theme,
                      result,
                      puzzle.glicko -> Glicko(
                        rating = puzzleRating.getRating
                          .atMost(puzzle.glicko.rating + Glicko.maxRatingDelta)
                          .atLeast(puzzle.glicko.rating - Glicko.maxRatingDelta),
                        deviation = puzzleRating.getRatingDeviation,
                        volatility = puzzleRating.getVolatility,
                      ).cap,
                      player = user.perfs.puzzle.glicko,
                    )
                    .some
                    .filter(puzzle.glicko !=)
                    .filter(_.sanityCheck)
                  val round =
                    PuzzleRound(
                      id = PuzzleRound.Id(user.id, puzzle.id),
                      win = result.win,
                      fixedAt = none,
                      date = DateTime.now,
                    )
                  val userPerf =
                    user.perfs.puzzle
                      .addOrReset(_.puzzle.crazyGlicko, s"puzzle ${puzzle.id}")(
                        userRating,
                        now,
                      ) pipe { p =>
                      p.copy(glicko =
                        ponder.player(
                          theme,
                          result,
                          user.perfs.puzzle.glicko -> p.glicko,
                          puzzle.glicko,
                        ),
                      )
                    }
                  (round, newPuzzleGlicko, userPerf)
              }
              api.round.upsert(round, theme) zip
                colls.puzzle {
                  _.update
                    .one(
                      $id(puzzle.id),
                      $inc(Puzzle.BSONFields.plays -> $int(1)) ++ newPuzzleGlicko.?? { glicko =>
                        $set(Puzzle.BSONFields.glicko -> Glicko.glickoBSONHandler.write(glicko))
                      },
                    )
                    .void
                } zip
                (userPerf != user.perfs.puzzle).?? {
                  userRepo.setPerf(user.id, PerfType.Puzzle, userPerf.clearRecent) zip
                    historyApi.addPuzzle(user = user, completedAt = now, perf = userPerf) void
                } >>- {
                  if (prevRound.isEmpty)
                    Bus.publish(
                      Puzzle.UserResult(
                        puzzle.id,
                        user.id,
                        result,
                        formerUserRating -> userPerf.intRating,
                      ),
                      "finishPuzzle",
                    )
                } inject (round -> userPerf).some
            }
          }
        }
      }

  private object ponder {

    // themes that don't hint at the solution
    private val nonHintingThemes: Set[PuzzleTheme.Key] = Set(
      PuzzleTheme.opening,
      PuzzleTheme.middlegame,
      PuzzleTheme.endgame,
      PuzzleTheme.tsume, // ?
      PuzzleTheme.lishogiGames,
      PuzzleTheme.otherSources,
    ).map(_.key)

    private def isHinting(theme: PuzzleTheme.Key) = !nonHintingThemes(theme)

    // themes that make the solution very obvious
    private val isObvious: Set[PuzzleTheme.Key] = Set(
      PuzzleTheme.mateIn1,
    ).map(_.key)

    private def weightOf(theme: PuzzleTheme.Key, result: Result) =
      if (theme == PuzzleTheme.mix.key) 1
      else if (isObvious(theme)) {
        if (result.win) 0.2f else 0.6f
      } else if (isHinting(theme)) {
        if (result.win) 0.3f else 0.7f
      } else {
        if (result.win) 0.7f else 0.8f
      }

    def player(theme: PuzzleTheme.Key, result: Result, glicko: (Glicko, Glicko), puzzle: Glicko) = {
      val provisionalPuzzle = puzzle.provisional ?? {
        if (result.win) -0.2f else -0.7f
      }
      glicko._1.average(glicko._2, (weightOf(theme, result) + provisionalPuzzle) atLeast 0.1f)
    }

    def puzzle(theme: PuzzleTheme.Key, result: Result, glicko: (Glicko, Glicko), player: Glicko) =
      if (player.clueless) glicko._1
      else glicko._1.average(glicko._2, weightOf(theme, result))
  }

  private val VOLATILITY = Glicko.default.volatility
  private val TAU        = 0.75d
  private val calculator = new RatingCalculator(VOLATILITY, TAU)

  def incPuzzlePlays(puzzleId: Puzzle.Id): Funit =
    colls.puzzle.map(_.incFieldUnchecked($id(puzzleId), Puzzle.BSONFields.plays))

  def batchIncPuzzlePlays(puzzleIds: List[Puzzle.Id]): Funit =
    puzzleIds.map(incPuzzlePlays).sequenceFu.void

  private def updateRatings(u1: Rating, u2: Rating, result: Glicko.Result): Unit = {
    val results = new RatingPeriodResults()
    result match {
      case Glicko.Result.Draw => results.addDraw(u1, u2)
      case Glicko.Result.Win  => results.addResult(u1, u2)
      case Glicko.Result.Loss => results.addResult(u2, u1)
    }
    try {
      calculator.updateRatings(results)
    } catch {
      case e: Exception => logger.error("finisher", e)
    }
  }

}
