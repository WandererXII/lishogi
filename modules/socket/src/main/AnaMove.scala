package lishogi.socket

import shogi.format.{ FEN, Uci, UciCharPair }
import shogi.opening._
import shogi.variant.Variant
import play.api.libs.json._
import scalaz.Validation.FlatMap._

import lishogi.tree.Branch

trait AnaAny {

  def branch: Valid[Branch]
  def chapterId: Option[String]
  def path: String
}

case class AnaMove(
    orig: shogi.Pos,
    dest: shogi.Pos,
    variant: Variant,
    fen: String,
    path: String,
    chapterId: Option[String],
    promotion: Boolean
) extends AnaAny {

  def branch: Valid[Branch] = {
    shogi.Game(variant.some, fen.some)(orig, dest, promotion) flatMap { case (game, move) =>
      game.pgnMoves.lastOption toValid "Moved but no last move!" map { san =>
        val uci     = Uci(move)
        val movable = game.situation playable false
        val fen     = shogi.format.Forsyth >> game
        Branch(
          id = UciCharPair(uci),
          ply = game.turns,
          move = Uci.WithSan(uci, san),
          fen = fen,
          check = game.situation.check,
          dests = Some(movable ?? game.situation.destinations),
          opening = (game.turns <= 30 && Variant.openingSensibleVariants(variant)) ?? {
            FullOpeningDB findByFen FEN(fen)
          },
          drops = if (movable) game.situation.drops else Some(Nil),
          crazyData = game.situation.board.crazyData
        )
      }
    }
  }
  // def json(b: Branch): JsObject = Json.obj(
  //   "node" -> b,
  //   "path" -> path
  // ).add("ch" -> chapterId)
}

object AnaMove {

  def parse(o: JsObject) = {
    for {
      d    <- o obj "d"
      orig <- d str "orig" flatMap shogi.Pos.posAt
      dest <- d str "dest" flatMap shogi.Pos.posAt
      fen  <- d str "fen"
      path <- d str "path"
    } yield AnaMove(
      orig = orig,
      dest = dest,
      variant = shogi.variant.Variant orDefault ~d.str("variant"),
      fen = fen,
      path = path,
      chapterId = d str "ch",
      promotion = (d \ "promotion").as[Boolean]
    )
  }
}
