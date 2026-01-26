package lila.shoginet

import shogi.format.usi.Usi
import shogi.variant.Standard

import lila.game.Game

object OpeningBook {

  def valid(game: Game): Boolean =
    supportedVariants.contains(game.variant) &&
      !game.fromPosition &&
      game.playedPlies <= maxDepth

  def find(game: Game): Option[Usi] = find(game.variant, game.usis)
  def find(variant: shogi.variant.Variant, usis: Seq[Usi]): Option[Usi] =
    variant match {
      case Standard => findCandidates(usis.map(_.usi).toList, standard).flatMap(pickWeighted)
      case _        => none
    }

  private val supportedVariants = List(Standard)

  private def findCandidates(path: List[String], nodes: List[MoveNode]): Option[List[MoveNode]] =
    path match {
      case Nil =>
        if (nodes.nonEmpty) Some(nodes) else None
      case head :: tail =>
        nodes.find(_.usi == head).flatMap(node => findCandidates(tail, node.children))
    }

  private def pickWeighted(candidates: List[MoveNode]): Option[Usi] = {
    val totalWeight = candidates.map(_.weight.value).sum
    if (totalWeight <= 0) return None

    val target = lila.common.ThreadLocalRandom.nextInt(totalWeight)

    def select(remaining: List[MoveNode], currentSum: Int): Option[String] = remaining match {
      case Nil => None
      case head :: tail =>
        val nextSum = currentSum + head.weight.value
        if (target < nextSum) Some(head.usi)
        else select(tail, nextSum)
    }

    select(candidates, 0).flatMap(Usi.apply)
  }

  // higher number -> more likely this move is going to be played
  case class Weight(value: Int) extends AnyVal
  case class MoveNode(usi: String, weight: Weight, children: List[MoveNode] = Nil)

  private[shoginet] val maxDepth = 2
  private[shoginet] val standard = List(
    // P-76
    MoveNode(
      "7g7f",
      Weight(11),
      List(
        MoveNode("3c3d", Weight(20)), // P-34
        MoveNode("8c8d", Weight(15)), // P-84
        MoveNode("4a3b", Weight(2)),  // G-32
        MoveNode("5c5d", Weight(2)),  // P-54
        MoveNode("8b3b", Weight(1)),  // R-32
        MoveNode("7a6b", Weight(1)),  // S-62
        MoveNode("8b5b", Weight(1)),  // R-52
        MoveNode("1c1d", Weight(1)),  // P-14
        MoveNode("7c7d", Weight(1)),  // P-74
        MoveNode("9c9d", Weight(1)),  // P-94
        MoveNode("8b4b", Weight(1)),  // R-42
      ),
    ),
    // P-26
    MoveNode(
      "2g2f",
      Weight(5),
      List(
        MoveNode("3c3d", Weight(10)), // P-34
        MoveNode("8c8d", Weight(2)),  // P-84
        MoveNode("4a3b", Weight(1)),  // G-32
        MoveNode("7a6b", Weight(1)),  // S-62
        MoveNode("8b5b", Weight(1)),  // R-52
      ),
    ),
    // P-56
    MoveNode(
      "5g5f",
      Weight(4),
      List(
        MoveNode("3c3d", Weight(3)), // P-34
        MoveNode("8c8d", Weight(2)), // P-84
        MoveNode("7a6b", Weight(1)), // S-62
        MoveNode("5c5d", Weight(1)), // P-54
      ),
    ),
    // R-68
    MoveNode(
      "2h6h",
      Weight(1),
      List(
        MoveNode("3c3d", Weight(3)), // P-34
        MoveNode("7a6b", Weight(2)), // S-62
        MoveNode("8c8d", Weight(1)), // P-84
        MoveNode("5c5d", Weight(1)), // P-54
      ),
    ),
    // P-96
    MoveNode(
      "9g9f",
      Weight(1),
      List(
        MoveNode("3c3d", Weight(1)), // P-34
        MoveNode("8c8d", Weight(1)), // P-84
        MoveNode("9c9d", Weight(1)), // P-94
      ),
    ),
    // P-16
    MoveNode(
      "1g1f",
      Weight(1),
      List(
        MoveNode("3c3d", Weight(1)), // P-34
        MoveNode("8c8d", Weight(1)), // P-84
        MoveNode("1c1d", Weight(1)), // P-14
      ),
    ),
    // R-78
    MoveNode(
      "2h7h",
      Weight(1),
      List(
        MoveNode("8c8d", Weight(1)), // P-84
        MoveNode("3c3d", Weight(1)), // P-34
      ),
    ),
    // G-78
    MoveNode(
      "6i7h",
      Weight(1),
      List(
        MoveNode("3c3d", Weight(1)), // P-34
        MoveNode("8c8d", Weight(1)), // P-84
      ),
    ),
    // P-36
    MoveNode(
      "3g3f",
      Weight(1),
      List(
        MoveNode("3c3d", Weight(1)), // P-34
        MoveNode("8c8d", Weight(1)), // P-84
      ),
    ),
    // R-58
    MoveNode(
      "2h5h",
      Weight(1),
      List(
        MoveNode("3c3d", Weight(1)), // P-34
        MoveNode("8c8d", Weight(1)), // P-84
      ),
    ),
    // P-66
    MoveNode(
      "6g6f",
      Weight(1),
      List(
        MoveNode("3c3d", Weight(1)), // P-34
      ),
    ),
    // S-48
    MoveNode(
      "3i4h",
      Weight(1),
      List(
        MoveNode("3c3d", Weight(1)), // P-34
      ),
    ),
    // K-68
    MoveNode(
      "5i6h",
      Weight(1),
      List(
        MoveNode("3c3d", Weight(1)), // P-34
      ),
    ),
  )

}
