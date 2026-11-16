package lila.shoginet

import lila.shoginet.JsonApi.Request.Evaluation

final private class ShoginetEvalCache(
    evalCacheApi: lila.evalCache.EvalCacheApi,
)(implicit ec: scala.concurrent.ExecutionContext) {

  val maxPlies = 12

  // indexes of positions to skip
  def skipPositions(game: Work.Game): Fu[List[Int]] =
    rawEvals(game).dmap(_.map(_._1))

  def evals(work: Work.Analysis): Fu[Map[Int, Evaluation]] =
    rawEvals(work.game) map {
      _.map { case (i, eval) =>
        val pv = eval.pvs.head
        i -> Evaluation(
          pv = pv.moves.value.toList,
          score = Evaluation
            .Score(
              cp = pv.score.cp,
              mate = pv.score.mate,
            )
            .invertIf((i + work.startPly) % 2 == 1), // shoginet evals are from POV
          time = none,
          nodes = eval.knodes.intNodes.some,
          nps = none,
          depth = eval.depth.some,
        )
      }.toMap
    }

  private def rawEvals(game: Work.Game): Fu[List[(Int, lila.evalCache.EvalCacheEntry.Eval)]] =
    shogi.Replay
      .positionsWhileValid(
        game.usiList.take(maxPlies - 1),
        game.initialSfen,
        game.variant,
      ) match {
      case (positions, None) =>
        positions.zipWithIndex
          .map { case (position, index) =>
            evalCacheApi.getSinglePvEval(
              game.variant,
              position.toSfenWithoutMoveNumber,
            ) dmap2 { index -> _ }
          }
          .sequenceFu
          .map(_.flatten)
      case _ => fuccess(Nil)
    }
}
