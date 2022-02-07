package shogi

import cats.data.Validated
import cats.syntax.option._
import org.specs2.matcher.Matcher
import org.specs2.matcher.ValidatedMatchers
import org.specs2.mutable.Specification

import scala.annotation.nowarn

import shogi.format.{ Forsyth, Visual }
import shogi.variant._

trait ShogiTest extends Specification with ValidatedMatchers {

  implicit def stringToBoard(str: String): Board = (Visual << str).get

  implicit def stringToSituationBuilder(str: String) =
    new {

      def as(color: Color): Situation = Situation((Visual << str).get, color)
    }

  case class RichActor(actor: Actor) {
    def threatens(to: Pos): Boolean =
      actor.piece.eyes(actor.pos, to) && {
        (!actor.piece.longRangeDirs.nonEmpty) ||
        (actor.pos touches to) ||
        actor.piece.role.dir(actor.pos, to).exists {
          Actor.longRangeThreatens(actor.board, actor.pos, _, to)
        }
      }
  }

  implicit def richActor(actor: Actor) = RichActor(actor)

  case class RichGame(game: Game) {

    def as(color: Color): Game = game.withPlayer(color)

    def playMoves(moves: (Pos, Pos)*): Validated[String, Game] = playMoveList(moves)

    @nowarn def playMoveList(moves: Seq[(Pos, Pos)]): Validated[String, Game] = {
      val vg = moves.foldLeft(Validated.valid(game): Validated[String, Game]) { (vg, move) =>
        vg.foreach { _.situation.destinations }
        val ng = vg flatMap { g =>
          g(move._1, move._2) map (_._1)
        }
        ng
      }
      vg
    }

    def playMove(
        orig: Pos,
        dest: Pos,
        promotion: Boolean = false
    ): Validated[String, Game] =
      game.apply(orig, dest, promotion) map (_._1)

    def playDrop(
        role: Role,
        dest: Pos
    ): Validated[String, Game] =
      game.drop(role, dest) map (_._1)

    def withClock(c: Clock) = game.copy(clock = Option(c))
  }

  implicit def richGame(game: Game) = RichGame(game)

  def fenToGame(positionString: String, variant: Variant = shogi.variant.Standard) = {
    val situation = Forsyth << positionString
    situation map { sit =>
      sit.color -> sit.withVariant(variant).board
    } toValid "Could not construct situation from SFEN" map { case (color, board) =>
      Game(variant).copy(
        situation = Situation(board, color)
      )
    }
  }

  def makeBoard(pieces: (Pos, Piece)*): Board =
    Board(pieces toMap, History(), shogi.variant.Standard)

  def makeBoard(str: String, variant: Variant) =
    (Visual.<<@(str, variant)).get

  def makeBoard: Board = Board init shogi.variant.Standard

  def makeEmptyBoard: Board = Board empty shogi.variant.Standard

  def bePoss(poss: Pos*): Matcher[Option[Iterable[Pos]]] =
    beSome.like { case p =>
      sortPoss(p.toList) must_== sortPoss(poss.toList)
    }

  def makeGame: Game = Game(makeBoard, Sente)

  def makeHand(
      pawn: Int = 0,
      lance: Int = 0,
      knight: Int = 0,
      silver: Int = 0,
      gold: Int = 0,
      bishop: Int = 0,
      rook: Int = 0
  ): Hand =
    Hand(
      Map(
        Pawn   -> pawn,
        Lance  -> lance,
        Knight -> knight,
        Silver -> silver,
        Gold   -> gold,
        Bishop -> bishop,
        Rook   -> rook
      )
    )

  def bePoss(board: Board, visual: String): Matcher[Option[Iterable[Pos]]] =
    beSome.like { case p =>
      Visual.addNewLines(Visual.>>|(board, Map(p -> 'x'))) must_== visual
    }

  def beBoard(visual: String): Matcher[Validated[String, Board]] =
    beValid.like { case b =>
      b.visual must_== ((Visual << visual).get).visual
    }

  def beSituation(visual: String): Matcher[Validated[String, Situation]] =
    beValid.like { case s =>
      s.board.visual must_== ((Visual << visual).get).visual
    }

  def beGame(visual: String): Matcher[Validated[String, Game]] =
    beValid.like { case g =>
      g.board.visual must_== ((Visual << visual).get).visual
    }

  def sortPoss(poss: Seq[Pos]): Seq[Pos] = poss sortBy (_.toString)

  def pieceMoves(piece: Piece, pos: Pos): Option[List[Pos]] =
    (makeEmptyBoard.place(piece, pos)) flatMap { b =>
      b actorAt pos map (_.destinations.distinct)
    }
}
