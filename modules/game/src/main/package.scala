package lishogi

package object game extends PackageObject {

  type PgnMoves    = Vector[String]
  type RatingDiffs = shogi.Color.Map[Int]

  private[game] def logger = lishogi.log("game")
}
