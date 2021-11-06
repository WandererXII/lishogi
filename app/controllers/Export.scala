package controllers

import akka.stream.scaladsl._
import akka.util.ByteString
import scala.concurrent.duration._
import play.api.mvc.Result

import shogi.Color
import shogi.format.Uci
import lishogi.app._
import lishogi.common.HTTPRequest
import lishogi.game.Pov
import lishogi.puzzle.Puzzle.Id

final class Export(env: Env) extends LishogiController(env) {

  private val ExportImageRateLimitGlobal = new lishogi.memo.RateLimit[String](
    credits = 600,
    duration = 1.minute,
    key = "export.image.global"
  )
  private val ExportGifRateLimitGlobal = new lishogi.memo.RateLimit[String](
    credits = 240,
    duration = 1.minute,
    key = "export.gif.global"
  )

  def gif(id: String, color: String) =
    Open { implicit ctx =>
      OnlyHumansAndFacebookOrTwitter {
        ExportGifRateLimitGlobal("-", msg = HTTPRequest.lastRemoteAddress(ctx.req).value) {
          OptionFuResult(env.game.gameRepo gameWithInitialFen id) { case (game, initialFen) =>
            val pov = Pov(game, Color(color) | Color.sente)
            env.game.gifExport.fromPov(pov, initialFen) map
              stream("image/gif") map
              gameImageCacheSeconds(game)
          }
        }(rateLimitedFu)
      }
    }

  def legacyGameThumbnail(id: String) =
    Action {
      MovedPermanently(routes.Export.gameThumbnail(id).url)
    }

  def gameThumbnail(id: String) =
    Open { implicit ctx =>
      ExportImageRateLimitGlobal("-", msg = HTTPRequest.lastRemoteAddress(ctx.req).value) {
        OptionFuResult(env.game.gameRepo game id) { game =>
          env.game.gifExport.gameThumbnail(game) map
            stream("image/gif") map
            gameImageCacheSeconds(game)
        }
      }(rateLimitedFu)
    }

  def puzzleThumbnail(id: String) =
    Open { implicit ctx =>
      ExportImageRateLimitGlobal("-", msg = HTTPRequest.lastRemoteAddress(ctx.req).value) {
        OptionFuResult(env.puzzle.api.puzzle find Id(id)) { puzzle =>
          env.game.gifExport.thumbnail(
            sfen = puzzle.fenAfterInitialMove,
            lastMove = Uci(puzzle.lastMove) map { _.usi },
            orientation = puzzle.color
          ) map stream("image/gif") map { res =>
            res.withHeaders(CACHE_CONTROL -> "max-age=86400")
          }
        }
      }(rateLimitedFu)
    }

  private def gameImageCacheSeconds(game: lishogi.game.Game)(res: Result): Result = {
    val cacheSeconds =
      if (game.finishedOrAborted) 3600 * 24
      else 10
    res.withHeaders(CACHE_CONTROL -> s"max-age=$cacheSeconds")
  }

  private def stream(contentType: String)(stream: Source[ByteString, _]) =
    Ok.chunked(stream).withHeaders(noProxyBufferHeader) as contentType
}
