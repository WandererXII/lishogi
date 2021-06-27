package shogi

import format.{ kif, Uci }

case class Game(
    situation: Situation,
    pgnMoves: Vector[String] = Vector(),
    clock: Option[Clock] = None,
    plies: Int = 0,
    startedAtPly: Int = 0
) {
  def apply(
      orig: Pos,
      dest: Pos,
      promotion: Boolean = false,
      metrics: MoveMetrics = MoveMetrics()
  ): Valid[(Game, Move)] = {
    situation.move(orig, dest, promotion).map(_ withMetrics metrics) map { move =>
      apply(move) -> move
    }
  }

  def apply(move: Move): Game = {
    val newSituation = move situationAfter

    copy(
      situation = newSituation,
      plies = plies + 1,
      pgnMoves = pgnMoves :+ kif.Dumper(situation, move),
      clock = applyClock(move.metrics, newSituation.status.isEmpty)
    )
  }

  def drop(
      role: Role,
      pos: Pos,
      metrics: MoveMetrics = MoveMetrics()
  ): Valid[(Game, Drop)] =
    situation.drop(role, pos).map(_ withMetrics metrics) map { drop =>
      applyDrop(drop) -> drop
    }

  def applyDrop(drop: Drop): Game = {
    val newSituation = drop situationAfter

    copy(
      situation = newSituation,
      plies = plies + 1,
      pgnMoves = pgnMoves :+ kif.Dumper(drop),
      clock = applyClock(drop.metrics, newSituation.status.isEmpty)
    )
  }

  private def applyClock(metrics: MoveMetrics, gameActive: Boolean) =
    clock.map { c =>
      {
        val newC = c.step(metrics, gameActive)
        if (plies == 1) newC.start else newC
      }
    }

  def apply(uci: Uci.Move): Valid[(Game, Move)] = apply(uci.orig, uci.dest, uci.promotion)
  def apply(uci: Uci.Drop): Valid[(Game, Drop)] = drop(uci.role, uci.pos)
  def apply(uci: Uci): Valid[(Game, MoveOrDrop)] = {
    uci match {
      case u: Uci.Move => apply(u) map { case (g, m) => g -> Left(m) }
      case u: Uci.Drop => apply(u) map { case (g, d) => g -> Right(d) }
    }
  }

  def player = situation.color

  def board = situation.board

  def isStandardInit = board.pieces == shogi.variant.Standard.pieces

  /** Turn number: The number of the pair of moves.
    * It starts at 1, and is incremented after Gote's move.
    */
  def turnNumber: Int = 1 + (startedAtPly + plies) / 2

  def moveNumber: Int = 1 + plies

  def moveString = s"${moveNumber}."

  def withBoard(b: Board) = copy(situation = situation.copy(board = b))

  def updateBoard(f: Board => Board) = withBoard(f(board))

  def withPlayer(c: Color) = copy(situation = situation.copy(color = c))

  def withPlies(p: Int) = copy(plies = p)
}

object Game {
  def apply(variant: shogi.variant.Variant): Game =
    new Game(
      Situation(Board init variant, Sente)
    )

  def apply(board: Board): Game = apply(board, Sente)

  def apply(board: Board, color: Color): Game = new Game(Situation(board, color))

  def apply(variantOption: Option[shogi.variant.Variant], fen: Option[String]): Game = {
    val variant = variantOption | shogi.variant.Standard
    val g       = apply(variant)
    fen
      .flatMap {
        format.Forsyth.<<<@(variant, _)
      }
      .fold(g) { parsed =>
        g.copy(
          situation = Situation(
            board = parsed.situation.board withVariant g.board.variant withCrazyData {
              parsed.situation.board.crazyData orElse g.board.crazyData
            },
            color = parsed.situation.color
          ),
          plies = parsed.moveNumber
        )
      }
  }
}
