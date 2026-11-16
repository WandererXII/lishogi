package lila.puzzle

import cats.data.NonEmptyList

import shogi.Color
import shogi.format.forsyth.Sfen
import shogi.format.usi.Usi

import lila.common.Iso
import lila.rating.Glicko

case class Puzzle(
    id: Puzzle.Id,
    sfen: Sfen,
    line: NonEmptyList[Usi],
    glicko: Glicko,
    plays: Int,
    vote: Float, // denormalized ratio of voteUp/voteDown
    gameId: Option[lila.game.Game.ID],
    themes: Set[PuzzleTheme.Key],
    author: Option[String] = None,
    description: Option[String] = None,
    submittedBy: Option[String] = None,
) {

  // ply after "initial move" when we start solving
  def initialPly: Int = {
    val stepNumber = sfen.stepNumber | 1
    stepNumber - (if ((stepNumber % 2 == 1) == (sfen.color | shogi.Sente).sente) 1 else 0)
  }

  lazy val sfenAfterInitialMove: Sfen =
    (if (gameId.isDefined)
       for {
         pos1 <- sfen.toPosition(shogi.variant.Standard)
         pos2 <- pos1(line.head).toOption
       } yield pos2.toSfenWithoutMoveNumber
     else sfen.some).getOrElse(sfen)

  def color: Color =
    if (gameId.isDefined) sfen.color.fold[Color](shogi.Sente)(!_)
    else sfen.color.getOrElse(shogi.Sente)

  def lastUsi: String =
    if (gameId.isDefined) line.head.usi
    else ""
}

object Puzzle {

  val idSize = 5

  case class Id(value: String) extends AnyVal with StringValue

  def toId(id: String) = id.sizeIs == idSize option Id(id)

  def glickoDefault(nbMoves: Int) = Glicko(1000d + nbMoves * 150d, 500d, 0.08d)

  case class UserResult(
      puzzleId: Id,
      userId: lila.user.User.ID,
      result: Result,
      rating: (Int, Int),
  )

  object BSONFields {
    val id          = "_id"
    val gameId      = "gameId"
    val sfen        = "sfen"
    val line        = "line"
    val glicko      = "glicko"
    val vote        = "vote"
    val voteUp      = "vu"
    val voteDown    = "vd"
    val plays       = "plays"
    val themes      = "themes"
    val day         = "day"
    val dirty       = "dirty" // themes need to be denormalized
    val author      = "a"
    val description = "dsc"
    val submittedBy = "sb"
  }

  implicit val idIso: Iso.StringIso[Id] = lila.common.Iso.string[Id](Id.apply, _.value)
}
