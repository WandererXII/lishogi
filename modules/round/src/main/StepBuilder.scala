package lila.round

import play.api.libs.json._

import shogi.format.forsyth.Sfen
import shogi.format.usi.Usi
import shogi.variant.Variant

import lila.socket.Step

object StepBuilder {

  private val logger = lila.round.logger.branch("StepBuilder")

  def apply(
      id: String,
      usis: Vector[Usi],
      variant: Variant,
      initialSfen: Option[Sfen],
  ): JsArray = {
    shogi.Replay.positionsWhileValid(usis, initialSfen, variant) match {
      case (positions, error) =>
        error foreach logShogiError(id)
        // an unreadable initial sfen yields no positions, fall back to the variant's default
        val (init, moved) = positions match {
          case head :: tail => (head, tail)
          case Nil          => (shogi.Position(variant), Nil)
        }
        JsArray {
          val initStep = Step(
            ply = init.plies,
            usi = none,
            sfen = init.toSfen,
            check = init.check,
          )
          val moveSteps = moved.zip(usis).map { case (p, u) =>
            Step(
              ply = p.plies,
              usi = u.some,
              sfen = p.toSfen,
              check = p.check,
            )
          }
          (initStep :: moveSteps).map(_.toJson)
        }
    }
  }

  private val logShogiError = (id: String) =>
    (err: String) => {
      val path = if (id == "synthetic") "analysis" else id
      logger.info(s"https://lishogi.org/$path ${err.linesIterator.toList.headOption | "?"}")
    }
}
