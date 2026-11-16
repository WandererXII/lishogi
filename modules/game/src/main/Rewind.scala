package lila.game

import cats.data.Validated
import org.joda.time.DateTime

import shogi.Replay

object Rewind {

  def apply(game: Game): Validated[String, Progress] =
    Replay
      .fromUsi(
        usis = game.usis.dropRight(1),
        initialSfen = game.initialSfen,
        variant = game.variant,
      )
      .valid
      .map { rewinded =>
        val color        = game.turnColor
        val turn         = rewinded.fullTurnNumber
        val refundPeriod = ~(game.clockHistory map (_.countSpentPeriods(!color, turn)))

        val newClock = game.clock.map(_.refundPeriods(!color, refundPeriod).takeback) map { clk =>
          game.clockHistory
            .flatMap { ch =>
              if (ch.firstEnteredPeriod(color).exists(_ < turn)) clk.byoyomiOf(color).some
              else ch.last(color)
            }
            .fold(clk) { t =>
              clk.setRemainingTime(color, t)
            }
        }
        def rewindPlayer(player: Player) = player.copy(proposeTakebackAt = 0)
        val newGame = game.copy(
          sentePlayer = rewindPlayer(game.sentePlayer),
          gotePlayer = rewindPlayer(game.gotePlayer),
          position = rewinded,
          clock = newClock,
          binaryMoveTimes = game.binaryMoveTimes.map { binary =>
            val moveTimes = BinaryFormat.moveTime.read(binary, game.playedPlies)
            BinaryFormat.moveTime.write(moveTimes.dropRight(1))
          },
          loadClockHistory = _ =>
            game.clockHistory.map { ch =>
              (ch.update(!color, _.dropRight(1))).refundSpentPeriods(!color, turn)
            },
          movedAt = DateTime.now,
        )
        Progress(game, newGame)
      }
}
