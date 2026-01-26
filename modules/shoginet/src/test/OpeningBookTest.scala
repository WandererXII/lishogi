package lila.shoginet

import org.specs2.mutable._

import shogi.Situation
import shogi.format.usi.Usi
import shogi.variant.Standard

final class OpeningBookTest extends Specification {

  "opening book" should {
    "contain only valid USI moves on all paths" in {

      def checkNode(sit: Situation, node: OpeningBook.MoveNode): Boolean = {
        val next = sit(Usi(node.usi).get)
        next.isValid must beTrue
        node.children.forall(checkNode(next.toOption.get, _))
      }

      val root = Situation(shogi.variant.Standard)

      OpeningBook.standard forall { node =>
        checkNode(root, node)
      }
    }

    "proper max depth set" in {
      def getDepth(nodes: List[OpeningBook.MoveNode]): Int =
        if (nodes.isEmpty) 0
        else nodes.map(node => 1 + getDepth(node.children)).max

      val actualMaxDepth = getDepth(OpeningBook.standard)

      actualMaxDepth must_== OpeningBook.maxDepth
    }

    "return a valid move from the root" in {
      val result = OpeningBook.find(Standard, Nil)

      result must beSome.like { case usi =>
        OpeningBook.standard.map(_.usi) must contain(usi.usi)
      }
    }

    "return a valid move for a known sub-path" in {
      val history = List(Usi("7g7f").get)
      val result  = OpeningBook.find(Standard, history)

      result must beSome.like { case usi =>
        val validChildren = OpeningBook.standard.find(_.usi == "7g7f").get.children.map(_.usi)
        validChildren must contain(usi.usi)
      }
    }

    "return None for a move not in the book" in {
      val history = List(Usi("5i5h").get)
      OpeningBook.find(Standard, history) must beNone
    }

    "return None for uns variants" in {
      OpeningBook.find(shogi.variant.Minishogi, Nil) must beNone
    }
  }
}
