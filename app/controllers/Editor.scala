package controllers

import shogi.format.Forsyth
import shogi.Situation
import play.api.libs.json._

import lishogi.app._
import views._

final class Editor(env: Env) extends LishogiController(env) {

  private lazy val positionsJson = lishogi.common.String.html.safeJsonValue {
    JsArray(shogi.StartingPosition.all map { p =>
      Json.obj(
        "eco"  -> p.eco,
        "name" -> p.name,
        "fen"  -> p.fen
      )
    })
  }

  def index = load("")

  def load(urlFen: String) =
    Open { implicit ctx =>
      val fenStr = lishogi.common.String
        .decodeUriPath(urlFen)
        .map(_.replace('_', ' ').trim)
        .filter(_.nonEmpty)
        .orElse(get("fen"))
      fuccess {
        val situation = readFen(fenStr)
        Ok(
          html.board.editor(
            sit = situation,
            fen = Forsyth >> situation,
            positionsJson,
            animationDuration = env.api.config.editorAnimationDuration
          )
        )
      }
    }

  def data =
    Open { implicit ctx =>
      fuccess {
        val situation = readFen(get("fen"))
        Ok(
          html.board.bits.jsData(
            sit = situation,
            fen = Forsyth >> situation,
            animationDuration = env.api.config.editorAnimationDuration
          )
        ) as JSON
      }
    }

  private def readFen(fen: Option[String]): Situation =
    fen.map(_.trim).filter(_.nonEmpty).flatMap(Forsyth.<<<).map(_.situation) | Situation(
      shogi.variant.Standard
    )

  def game(id: String) =
    Open { implicit ctx =>
      OptionResult(env.game.gameRepo game id) { game =>
        Redirect {
          if (game.playable) routes.Round.watcher(game.id, "sente")
          else routes.Editor.load(get("fen") | (shogi.format.Forsyth >> game.shogi))
        }
      }
    }
}
