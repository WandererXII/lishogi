package lila.storm

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

import lila.db.dsl._
import lila.memo.CacheApi
import lila.puzzle.PuzzleColls

/* The difficulty of storm should remain constant!
 * Be very careful when adjusting the selector.
 * Use the grafana average rating per slice chart.
 */
final class StormSelector(colls: PuzzleColls, cacheApi: CacheApi)(implicit ec: ExecutionContext) {

  import StormBsonHandlers._

  def apply: Fu[List[StormPuzzle]] = current.get {}

  private val theme        = lila.puzzle.PuzzleTheme.mix.key.value
  private val tier         = lila.puzzle.PuzzleTier.Good.key
  private val maxDeviation = 600

  private val ratingBuckets =
    List(
      1000 -> 8,
      1150 -> 8,
      1300 -> 9,
      1450 -> 10,
      1600 -> 12,
      1750 -> 13,
      1900 -> 14,
      2050 -> 15,
      2200 -> 17,
      2350 -> 19,
      2500 -> 20,
    )
  private val poolSize = ratingBuckets.foldLeft(0) { case (acc, (_, nb)) =>
    acc + nb
  }

  private val current = cacheApi.unit[List[StormPuzzle]] {
    _.refreshAfterWrite(10 seconds)
      .buildAsyncFuture { _ =>
        colls
          .path {
            _.aggregateList(poolSize) { framework =>
              import framework._
              Facet(
                ratingBuckets.map { case (rating, nbPuzzles) =>
                  rating.toString -> List(
                    Match(
                      $doc(
                        "min" $lte f"${theme}_${tier}_${rating}%04d",
                        "max" $gte f"${theme}_${tier}_${rating}%04d",
                      ),
                    ),
                    Sample(1),
                    Project($doc("_id" -> false, "ids" -> true)),
                    UnwindField("ids"),
                    // ensure we have enough after filtering deviation
                    Sample(nbPuzzles * 5),
                    PipelineOperator(
                      $doc(
                        "$lookup" -> $doc(
                          "from" -> colls.puzzle.name.value,
                          "as"   -> "puzzle",
                          "let"  -> $doc("id" -> "$ids"),
                          "pipeline" -> $arr(
                            $doc(
                              "$match" -> $doc(
                                "$expr" -> $doc(
                                  "$and" -> $arr(
                                    $doc("$eq"  -> $arr("$_id", "$$id")),
                                    $doc("$lte" -> $arr("$glicko.d", maxDeviation)),
                                  ),
                                ),
                              ),
                            ),
                            $doc(
                              "$project" -> $doc(
                                "sfen"   -> true,
                                "line"   -> true,
                                "rating" -> $doc("$toInt" -> "$glicko.r"),
                              ),
                            ),
                          ),
                        ),
                      ),
                    ),
                    UnwindField("puzzle"),
                    Sample(nbPuzzles),
                    ReplaceRootField("puzzle"),
                  )
                },
              ) -> List(
                Project($doc("all" -> $doc("$setUnion" -> ratingBuckets.map(r => s"$$${r._1}")))),
                UnwindField("all"),
                ReplaceRootField("all"),
                Sort(Ascending("rating")),
                Limit(poolSize),
              )
            }.map {
              _.flatMap(StormPuzzleBSONReader.readOpt)
            }
          }
          .mon(_.storm.selector.time)
          .addEffect { puzzles =>
            monitor(puzzles.toVector, poolSize)
          }
      }
  }

  private def monitor(puzzles: Vector[StormPuzzle], poolSize: Int): Unit = {
    val nb = puzzles.size
    lila.mon.storm.selector.count.record(nb)
    if (nb < poolSize * 0.9)
      logger.warn(s"Selector wanted $poolSize puzzles, only got $nb")
    if (nb > 1) {
      val rest = puzzles.toVector drop 1
      lila.common.Maths.mean(rest.map(_.rating)) foreach { r =>
        lila.mon.storm.selector.rating.record(r.toInt)
      }
      (0 to poolSize by 10) foreach { i =>
        val slice = rest drop i take 10
        lila.common.Maths.mean(slice.map(_.rating)) foreach { r =>
          lila.mon.storm.selector.ratingSlice(i).record(r.toInt).unit
        }
      }
      colls.puzzle {
        _.update.one(
          $inIds(puzzles.map(_.id.value)),
          $inc("storm" -> 1),
          multi = true,
        )
      }.unit
    }
  }
}
