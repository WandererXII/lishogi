package lila.study

import org.specs2.mutable.Specification

import shogi.Gote
import shogi.format.forsyth.Sfen
import shogi.variant.Standard

class NotationImportTest extends Specification {

  "notation analysis" should {
    "build the tree from the initial gote-to-move position" in {
      NotationImport.userAnalysis("PI\n-\n-3334FU\n+7776FU").toOption must beSome.like {
        case (game, root, _) =>
          root.ply must_== 1
          root.sfen.toPosition(Standard).map(_.color) must_== Some(Gote)
          root.children.nodes.map(_.usi.usi) must_== Vector("3c3d")
          game.usis.map(_.usi) must_== Vector("3c3d", "7g7f")
      }
    }

    "retain spent durations, root configuration, and original notation controls" in {
      NotationImport
        .userAnalysis("$TIME_LIMIT:00:10+20\nPI\n+\n+7776FU\nT30\n-3334FU\nT21474837")
        .toOption must beSome.like { case (game, root, tags) =>
        game.clock.map(_.config) must beSome(lila.game.clock.Clock.Config(600, 0, 20, 1))
        root.clock must_== Some(lila.common.Centis(60000))
        root.children.nodes.head.clock must_== Some(lila.common.Centis(3000))
        root.children.nodes.head.children.nodes.head.clock must_== Some(
          lila.common.Centis(Int.MaxValue),
        )
        tags(_.TimeControl) must beSome("00:10+20")
      }
    }
    "preserve a separate imported byoyomi tag" in {
      NotationImport(
        "持ち時間：10分\n秒読み：20秒\n手数----指手---------消費時間--\n1 ７六歩(77)",
        Nil,
      ).toOption must beSome.like { case result =>
        result.tags(_.TimeControl) must beSome("10分")
        result.tags(_.Byoyomi) must beSome("20秒")
        lila.game.clock.NotationClock.config(result.tags) must_== Some(
          lila.game.clock.Clock.Config(600, 0, 20, 1),
        )
      }
    }
    "retain integer-centisecond BSON for study timing" in {
      val centis = lila.common.Centis(12345)
      BSONHandlers.CentisBSONHandler.writeTry(centis).get must_==
        reactivemongo.api.bson.BSONInteger(12345)
      BSONHandlers.CentisBSONHandler.readTry(reactivemongo.api.bson.BSONInteger(-12345)).get must_==
        lila.common.Centis(-12345)
    }
    "round parsed milliseconds at the study node boundary" in {
      NotationImport
        .userAnalysis("持ち時間：10分\n手数----指手---------消費時間--\n1 ７六歩(77) (0:00.0045/0:0:01)")
        .toOption must beSome.like { case (game, root, _) =>
        game.clock.map(_.config) must beSome(lila.game.clock.Clock.Config(600, 0, 0, 1))
        root.children.nodes.head.clock must_== Some(lila.common.Centis(1))
      }
    }
    "keep the initial root after an incomplete replay" in {
      NotationImport.userAnalysis("PI\n+\n+7776FU\n-3334FU\n+7776FU").toOption must beSome.like {
        case (game, root, _) =>
          root.sfen.truncate must_== Sfen(Standard.initialSfen.value).truncate
          root.children.nodes.map(_.usi.usi) must_== Vector("7g7f")
          game.usis.map(_.usi) must_== Vector("7g7f", "3c3d")
      }
    }
  }
}
