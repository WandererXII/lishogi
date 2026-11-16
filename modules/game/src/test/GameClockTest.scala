package lila.game

import org.joda.time.DateTime
import org.specs2.mutable.Specification

import shogi.Color
import shogi.Gote
import shogi.Sente
import shogi.Status
import shogi.format.forsyth.Sfen
import shogi.format.usi.Usi
import shogi.variant.Standard
import shogi.variant.Variant

import lila.common.Centis
import lila.game.clock.Clock
import lila.game.clock.LagMetrics
import lila.game.clock.Timestamp
import lila.game.clock.Timestamper

class GameClockTest extends Specification {
  class Time(var millis: Long = 1000000L) extends Timestamper {
    def now                        = Timestamp(millis)
    def advance(centis: Int): Unit = millis += centis * 10L
  }

  def make(
      time: Time,
      sfen: Option[Sfen] = None,
      variant: Variant = Standard,
      config: Clock.Config = Clock.Config(60, 0, 0, 0),
  ): Game = {
    val board = shogi.Position(sfen, variant)
    Game
      .make(
        position = board,
        clock = Some(config.toClock(board.color).copy(timestamper = time)),
        initialSfen = sfen,
        sentePlayer = Player.make(Sente),
        gotePlayer = Player.make(Gote),
        mode = shogi.Mode.Casual,
        proMode = false,
        source = Source.Friend,
        notationImport = None,
      )
      .sloppy
      .start
  }

  def prepare(game: Game, move: String) =
    game.prepareMove(Usi(move).get, LagMetrics.empty).toOption.get
  def play(game: Game, move: String): Game = game.applyGame(prepare(game, move), false).game
  def opening(game: Game): Game            = play(play(game, "7g7f"), "3c3d")

  "owned live clocks" should {
    "leave the opening untimed and start after the second played ply" in {
      val time = new Time
      val game = make(time)
      time.advance(1000)
      val first = play(game, "7g7f")
      first.clock.get.isRunning must beFalse
      time.advance(1000)
      val second = play(first, "3c3d")
      second.clock.get.isRunning must beTrue
      second.clock.get.players.map(_.remaining) must_== Color.Map(Centis(6000), Centis(6000))
      second.clockHistory.get.size must_== 2
    }
    "validate the board before touching a clock" in {
      val time = new Time
      val game = opening(make(time))
      time.advance(100)
      game.prepareMove(Usi("7g7f").get, LagMetrics.empty).isInvalid must beTrue
      game.clock.get.players(Sente).elapsed must_== Centis(0)
      game.usis.size must_== 2
    }
    "prepare one debit, expose a flag before commit, and keep the original board" in {
      val time = new Time
      val game = opening(make(time))
      time.advance(6000)
      val candidate = prepare(game, "2g2f")
      candidate.clock.get.outOfTime(Sente, withGrace = false) must beTrue
      candidate.clock.get.isRunning must beFalse
      candidate.position.usis.size must_== 3
      game.usis.size must_== 2
      game.clock.get.players(Sente).remaining must_== Centis(6000)
    }
    "commit the prepared debit without stepping again" in {
      val time = new Time
      val game = opening(make(time))
      time.advance(100)
      val candidate = prepare(game, "2g2f")
      val next      = game.applyGame(candidate, false).game
      next.clock.get.players(Sente).remaining must_== Centis(5900)
      next.clockHistory.get.last(Sente) must_== Some(Centis(5900))
      next.binaryMoveTimes must beNone
    }
    "start from the relative ply and actual color of a gote SFEN" in {
      val time    = new Time
      val sfen    = Sfen(Standard.initialSfen.value.replace(" b ", " w ").replace(" 1", " 42"))
      val initial = make(time, Some(sfen))
      initial.turnColor must_== Gote
      initial.clock.get.color must_== Gote
      val first = play(initial, "3c3d")
      first.clock.get.isRunning must beFalse
      val second = play(first, "7g7f")
      second.clock.get.isRunning must beTrue
      second.clock.get.color must_== Gote
      time.advance(1000)
      val third = play(second, "8c8d")
      third.clock.get.players(Gote).remaining must_== Centis(5000)
      third.clock.get.players(Sente).remaining must_== Centis(6000)
    }
    "withhold increment on a terminal second move and retain starting step" in {
      val time = new Time
      val game = make(
        time,
        Some(Sfen("9/3gk4/9/2b6/9/6B2/9/4KG3/9 b - 12")),
        shogi.variant.Checkshogi,
        Clock.Config(60, 5, 0, 0),
      )
      val ended = play(play(game, "4h4g"), "7d8e")
      ended.position.status must_== Some(Status.Check)
      ended.clock.get.isRunning must beFalse
      ended.position.stepNumber must_== 14
      ended.clock.get.players(Gote).remaining must_== Centis(6000)
      ended.finish(Status.Check, Some(Gote)).clockHistory.get.size must_== 2
    }
    "seal and resume without charging the sealed move twice" in {
      val time = new Time
      val game = opening(make(time, config = Clock.Config(60, 5, 0, 0)))
      time.advance(1000)
      val candidate  = prepare(game, "2g2f")
      val sealedGame = game.pauseAndSealUsi(Usi("2g2f").get, candidate, false).game
      sealedGame.usis.size must_== 2
      sealedGame.clock.get.color must_== Gote
      sealedGame.clock.get.isRunning must beFalse
      sealedGame.clockHistory.get.size must_== 3
      time.advance(5000)
      val resumed = sealedGame.resume
      resumed.usis.size must_== 3
      resumed.clock.get.color must_== Gote
      resumed.clock.get.isRunning must beTrue
      resumed.clock.get.players(Sente).remaining must_== Centis(5500)
      resumed.clockHistory must_== sealedGame.clockHistory
    }
    "discard pending time on forced pause and debit it on finish" in {
      val time = new Time
      val game = opening(make(time))
      time.advance(1000)
      game.forcePause.clock.get.players(Sente).remaining must_== Centis(6000)
      val finished = game.finish(Status.Resign, Some(Gote))
      finished.clock.get.players(Sente).remaining must_== Centis(5000)
      finished.clock.get.isRunning must beFalse
      finished.clockHistory.get.size must_== 3
      finished.clockHistory.get.last(Sente) must_== Some(Centis(5000))
    }
    "refund byoyomi periods on single and double takebacks" in {
      val time = new Time
      val base = opening(make(time, config = Clock.Config(10, 0, 10, 3)))
      time.advance(2500)
      val third = play(base, "2g2f")
      third.clock.get.spentPeriodsOf(Sente) must_== 2
      time.advance(1500)
      val fourth = play(third, "8c8d")
      fourth.clock.get.spentPeriodsOf(Gote) must_== 1
      val single = Rewind(fourth).toOption.get.game
      single.clock.get.spentPeriodsOf(Gote) must_== 0
      single.clock.get.spentPeriodsOf(Sente) must_== 2
      single.clock.get.color must_== Gote
      single.clockHistory.get.size must_== 3
      val double = Rewind(single).toOption.get.game
      double.clock.get.spentPeriodsOf(Sente) must_== 0
      double.clock.get.color must_== Sente
      double.usis.size must_== 2
      double.clockHistory.get.size must_== 2
    }
    "preserve moretime and stopped-clock flagging" in {
      val time    = new Time
      val game    = opening(make(time))
      val granted = game.withClock(game.clock.get.giveTime(Sente, Centis(1000))).game
      granted.clock.get.players(Sente).remaining must_== Centis(7000)
      val expired = game.withClock(game.clock.get.giveTime(Sente, Centis(-6100)).hardStop).game
      expired.outoftime(withGrace = false) must beTrue
    }
    "persist sealed board and clock colors independently" in {
      val time = new Time
      val game = opening(make(time))
      time.advance(100)
      val sealedGame = game.pauseAndSealUsi(Usi("2g2f").get, prepare(game, "2g2f"), false).game
      val stored     = BSONHandlers.gameBSONHandler.write(sealedGame)
      val loaded     = BSONHandlers.gameBSONHandler.read(stored)
      loaded.turnColor must_== Sente
      loaded.clock.get.color must_== Gote
      loaded.clock.get.isRunning must beFalse
      loaded.clock.get.players(Sente).remaining must_== Centis(5900)
      loaded.clockHistory.get.size must_== 3
      loaded.resume.usis.size must_== 3
    }
    "continue loading clock payloads on saved imported games" in {
      val time   = new Time
      val game   = make(time).copy(metadata = Game.metadata(Source.Import))
      val stored = BSONHandlers.gameBSONHandler.write(game)
      val loaded = BSONHandlers.gameBSONHandler.read(stored)
      loaded.imported must beTrue
      loaded.clock.get.players(Sente).remaining must_== Centis(6000)
      loaded.clock.get.config must_== game.clock.get.config
    }
    "keep no-clock moves and their binary timing" in {
      val time = new Time
      val game = make(time).copy(clock = None, movedAt = DateTime.now.minusSeconds(5))
      val next = play(game, "7g7f")
      next.clock must beNone
      next.clockHistory must beNone
      next.binaryMoveTimes must beSome
    }
  }
}
