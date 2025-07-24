package lila.round

import shogi.Color
import shogi.DecayingStats
import shogi.Status

import lila.common.Bus
import lila.common.Uptime
import lila.game.Game
import lila.game.GameRepo
import lila.game.Pov
import lila.game.RatingDiffs
import lila.game.actorApi.AbortedBy
import lila.game.actorApi.FinishGame
import lila.i18n.{ I18nKeys => trans }
import lila.playban.PlaybanApi
import lila.user.User
import lila.user.UserRepo

final private class Finisher(
    gameRepo: GameRepo,
    userRepo: UserRepo,
    messenger: Messenger,
    perfsUpdater: PerfsUpdater,
    playban: PlaybanApi,
    notifier: RoundNotifier,
    crosstableApi: lila.game.CrosstableApi,
    getSocketStatus: Game => Fu[actorApi.SocketStatus],
    recentTvGames: RecentTvGames,
)(implicit ec: scala.concurrent.ExecutionContext) {

  def abort(pov: Pov)(implicit proxy: GameProxy): Fu[Events] =
    apply(pov.game, _.Aborted, none) >>- {
      getSocketStatus(pov.game) foreach { ss =>
        playban.abort(pov, ss.colorsOnGame)
      }
      Bus.publish(AbortedBy(pov.copy(game = pov.game.abort)), "abortGame")
    }

  def rageQuit(game: Game, winner: Option[Color])(implicit proxy: GameProxy): Fu[Events] =
    apply(game, _.Timeout, winner) >>-
      winner.foreach { color =>
        playban.rageQuit(game, !color)
      }

  def forcePause(game: Game)(implicit proxy: GameProxy): Fu[Events] = {
    val prog = lila.game.Progress(game, game.forcePause)
    proxy.save(prog)
    fuccess(List(lila.game.Event.Reload))
  }

  def outOfTime(game: Game)(implicit proxy: GameProxy): Fu[Events] = {
    if (
      !game.isCorrespondence && !Uptime
        .startedSinceSeconds(120) && game.movedAt.isBefore(Uptime.startedAt)
    ) {
      if (game.arrangementId.isDefined) {
        logger.info(s"Pausing tour game last played before JVM boot: ${game.id}")
        forcePause(game)
      } else {
        logger.info(s"Aborting game last played before JVM boot: ${game.id}")
        other(game, _.Aborted, none)
      }
    } else {
      val winner = Some(!game.player.color)
      apply(game, _.Outoftime, winner) >>-
        winner.foreach { w =>
          playban.flag(game, !w)
        }
    }
  }

  def noStart(game: Game)(implicit proxy: GameProxy): Fu[Events] =
    game.playerWhoDidNotMove ?? { culprit =>
      lila.mon.round.expiration.count.increment()
      playban.noStart(Pov(game, culprit))
      if (game.isMandatory) apply(game, _.NoStart, winner = Some(!culprit.color))
      else apply(game, _.Aborted, winner = none)
    } >>- messenger.system(game, trans.gameAborted)

  def illegal(game: Game)(implicit proxy: GameProxy): Fu[Events] = {
    val winner = !game.player.color
    apply(
      game,
      _.IllegalMove,
      winner = winner.some,
    ) >>- messenger.system(game, trans.xPlayedIllegalMove, (!winner, game.isHandicap).some)
  }

  def other(
      game: Game,
      status: Status.type => Status,
      winner: Option[Color],
  )(implicit proxy: GameProxy): Fu[Events] =
    apply(game, status, winner) >>- playban.other(game, status, winner).unit

  private def recordLagStats(game: Game): Unit =
    for {
      clock  <- game.clock
      player <- clock.players.all
      lt    = player.lag
      stats = lt.lagStats
      steps = lt.steps if steps > 4
      sd <- stats.stdDev
      mean        = stats.mean if mean > 0
      uncompStats = lt.uncompStats
      uncompAvg   = Math.round(10 * uncompStats.mean)
      compEstStdErr <- lt.compEstStdErr
      quotaStr     = f"${lt.quotaGain.centis / 10}%02d"
      compEstOvers = lt.compEstOvers.centis
    } {
      import lila.mon.round.move.{ lag => lRec }
      lRec.mean.record(Math.round(10 * mean))
      lRec.stdDev.record(Math.round(10 * sd))
      // wikipedia.org/wiki/Coefficient_of_variation#Estimation
      lRec.coefVar.record(Math.round((1000f + 250f / steps) * sd / mean))
      lRec.uncomped(quotaStr).record(uncompAvg)
      uncompStats.stdDev foreach { v =>
        lRec.uncompStdDev(quotaStr).record(Math.round(10 * v))
      }
      lt.lagEstimator match {
        case h: DecayingStats => lRec.compDeviation.record(h.deviation.toInt)
      }
      lRec.compEstStdErr.record(Math.round(1000 * compEstStdErr))
      lRec.compEstOverErr.record(Math.round(10f * compEstOvers / steps))
    }

  private def apply(
      prev: Game,
      makeStatus: Status.type => Status,
      winner: Option[Color],
  )(implicit proxy: GameProxy): Fu[Events] = {
    val status = makeStatus(Status)
    val prog   = lila.game.Progress(prev, prev.finish(status, winner))
    val game   = prog.game
    if (game.nonAi && game.isCorrespondence) Color.all foreach notifier.gameEnd(prog.game)
    lila.mon.game
      .finish(
        variant = game.variant.key,
        source = game.source.fold("unknown")(_.name),
        speed = game.speed.name,
        mode = game.mode.name,
        status = status.name,
      )
      .increment()
    recordLagStats(game)
    proxy.save(prog) >>
      gameRepo.finish(
        id = game.id,
        winnerColor = winner,
        winnerId = winner map (game.player(_).userId | ""),
        illegalUsi = prog.game.illegalUsi,
        status = prog.game.status,
      ) >>
      userRepo
        .pair(
          game.sentePlayer.userId,
          game.gotePlayer.userId,
        )
        .flatMap {
          case (senteO, goteO) => {
            val finish = FinishGame(game, senteO, goteO)
            updateCountAndPerfs(finish) map { ratingDiffs =>
              gameRepo game game.id foreach { newGame =>
                newGame foreach proxy.setFinishedGame
                val newFinish = finish.copy(game = newGame | game)
                Bus.publish(newFinish, "finishGame")
                game.userIds foreach { userId =>
                  Bus.publish(newFinish, s"userFinishGame:$userId")
                }
              }
              List(lila.game.Event.EndData(game, ratingDiffs))
            }
          }
        }
  }

  private def updateCountAndPerfs(finish: FinishGame): Fu[Option[RatingDiffs]] =
    (!finish.isVsSelf && !finish.game.aborted) ?? {
      import cats.implicits._
      (finish.sente, finish.gote).mapN((_, _)) ?? { case (sente, gote) =>
        crosstableApi.add(finish.game) zip perfsUpdater.save(finish.game, sente, gote) dmap (_._2)
      } zip
        (finish.sente ?? incNbGames(finish.game)) zip
        (finish.gote ?? incNbGames(finish.game)) dmap (_._1._1)
    }

  private def incNbGames(game: Game)(user: User): Funit =
    game.finished ?? { user.noBot || game.nonAi } ?? {
      val totalTime = (game.hasClock && user.playTime.isDefined) ?? game.durationSeconds
      val tvTime    = totalTime ifTrue recentTvGames.get(game.id)
      val result =
        if (game.winnerUserId has user.id) 1
        else if (game.loserUserId has user.id) -1
        else 0
      if (result == 1) updateAiLevels(game, user)
      userRepo
        .incNbGames(
          user.id,
          game.rated,
          game.hasAi,
          result = result,
          totalTime = totalTime,
          tvTime = tvTime,
        )
        .void
    }

  private def updateAiLevels(game: Game, user: User): Funit =
    (game.aiLevel.filter(level => user.perfs.aiLevels(game.variant).fold(true)(_ < level))) ?? {
      level =>
        userRepo.setPerfAiLevel(user.id, game.variant, level).void
    }
}
