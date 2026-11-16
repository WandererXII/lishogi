package lila.importer

import org.specs2.mutable.Specification

import shogi.Gote
import shogi.Sente

import lila.game.clock.Clock

class ImportDataTest extends Specification {

  private def preprocess(steps: String, turn: String = "+") =
    ImportData(s"PI\n$$TIME_LIMIT:00:10+00\n$turn\n$steps", None).preprocess(None)

  "notation imports" should {
    "import KIF moves and determine the resigning player's opponent" in {
      val kif = "手合割：平手\n手数----指手---------消費時間--\n1 ７六歩(77)\n2 ３四歩(33)\n3 投了"
      ImportData(kif, None).preprocess(None).toOption must beSome.like { case imported =>
        imported.game.sloppy.usis.map(_.usi) must_== Vector("7g7f", "3c3d")
        imported.game.sloppy.winnerColor must_== Some(Gote)
      }
    }

    "read the new CSA result tags" in {
      preprocess("+7776FU\n%TORYO").toOption must beSome.like { case imported =>
        imported.game.sloppy.winnerColor must_== Some(Sente)
      }
    }

    "keep the valid prefix of an incomplete replay" in {
      preprocess("+7776FU\n-3334FU\n+7776FU").toOption must beSome.like { case imported =>
        imported.game.sloppy.usis.map(_.usi) must_== Vector("7g7f", "3c3d")
        imported.setup.usis must beEmpty
      }
    }

    "retain the initial position when gote moves first" in {
      preprocess("-3334FU\n+7776FU", "-").toOption must beSome.like { case imported =>
        imported.setup.color must_== Gote
        imported.setup.plies must_== 1
        imported.game.sloppy.initialSfen must beSome(imported.setup.toSfen)
        imported.game.sloppy.usis.map(_.usi) must_== Vector("3c3d", "7g7f")
      }
    }

    "keep the notation time control without clock history while retaining notation times" in {
      preprocess("+7776FU\nT30\n-3334FU\nT10").toOption must beSome.like { case imported =>
        imported.game.sloppy.clock.map(_.config) must beSome(Clock.Config(600, 0, 0, 1))
        imported.game.sloppy.clockHistory must beNone
        imported.parsed.tags(_.TimeControl) must beSome("00:10+00")
        imported.parsed.parsedSteps.value.head.metas.timeSpent.map(_.toMillis) must beSome(30000L)
      }
    }

    "leave imports without a time control clockless" in {
      val kif = "手合割：平手\n手数----指手---------消費時間--\n1 ７六歩(77)\n2 ３四歩(33)\n3 投了"
      ImportData(kif, None).preprocess(None).toOption must beSome.like { case imported =>
        imported.game.sloppy.clock must beNone
      }
    }
  }
}
