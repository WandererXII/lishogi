package lila.game

import org.specs2.mutable.Specification

import shogi.Gote
import shogi.Sente
import shogi.format.forsyth.Sfen
import shogi.format.usi.Usi
import shogi.variant.Standard
import shogi.variant.Variant

import lila.game.clock.LagMetrics

class GameBSONTest extends Specification {

  import BSONHandlers.gameBSONHandler

  def make(sfen: Option[Sfen], variant: Variant = Standard): Game =
    Game
      .make(
        position = shogi.Position(sfen, variant),
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

  def play(game: Game, moves: String*): Game =
    moves.foldLeft(game) { (g, m) =>
      g.applyGame(g.prepareMove(Usi(m).get, LagMetrics.empty).toOption.get, false).game
    }

  def roundTrip(game: Game): Game = gameBSONHandler.read(gameBSONHandler.write(game))

  "game BSON" should {
    "not store the variant's own initial sfen" in {
      val game = play(make(Some(Standard.initialSfen)), "7g7f", "3c3d")
      game.initialSfen must beNone
      game.fromPosition must beFalse
      gameBSONHandler.write(game).contains(Game.BSONFields.initialSfen) must beFalse
      roundTrip(game).initialSfen must beNone
    }

    "restore the position of a game started with gote to move" in {
      val sfen = Sfen("lnsgkgsnl/9/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL w - 1")
      val game = play(make(Some(sfen)), "3c3d", "7g7f")
      val read = roundTrip(game)
      game.isHandicap must beTrue
      read.initialSfen must beSome(sfen)
      read.plies must_== 3
      read.playedPlies must_== 2
      read.startColor must_== Gote
      read.turnColor must_== Gote
      read.position.startedAtPly must_== 1
      read.position.startedAtStep must_== 1
      read.position.stepNumber must_== 3
      read.usis must_== game.usis
      read.position must_== game.position
      read.position.toSfen must_== game.position.toSfen
    }

    "restore the step numbering of a game started mid-game" in {
      val sfen = Sfen("lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL b - 13")
      val game = play(make(Some(sfen)), "7g7f")
      val read = roundTrip(game)
      read.position.startedAtPly must_== 12
      read.position.startedAtStep must_== 13
      read.plies must_== 13
      read.playedPlies must_== 1
      read.position must_== game.position
      read.position.toSfen.stepNumber must beSome(14)
      read.position.toSfenWithoutMoveNumber.stepNumber must beNone
    }
  }
}
