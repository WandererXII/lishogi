package controllers

import scala.annotation.nowarn
import scala.concurrent.duration._

import play.api.data.Forms._
import play.api.data._
import play.api.libs.json._
import play.api.mvc._
import views._

import akka.pattern.ask

import lila.app._
import lila.app.makeTimeout.large
import lila.common.HTTPRequest
import lila.hub.actorApi.captcha.ValidCaptcha

final class Main(
    env: Env,
    assetsC: ExternalAssets,
) extends LilaController(env) {

  private lazy val blindForm = Form(
    tuple(
      "enable"   -> nonEmptyText,
      "redirect" -> nonEmptyText,
    ),
  )

  def toggleBlindMode =
    OpenBody { implicit ctx =>
      implicit val req = ctx.body
      fuccess {
        blindForm
          .bindFromRequest()
          .fold(
            _ => BadRequest,
            { case (enable, redirect) =>
              Redirect(redirect) withCookies env.lilaCookie.cookie(
                env.api.config.accessibility.blindCookieName,
                if (enable == "0") "" else env.api.config.accessibility.hash,
                maxAge = env.api.config.accessibility.blindCookieMaxAge.toSeconds.toInt.some,
                httpOnly = true.some,
              )
            },
          )
      }
    }

  def handlerNotFound(req: RequestHeader) = reqToCtx(req) map renderNotFound

  def captchaCheck(id: String) =
    Open { implicit ctx =>
      env.hub.captcher.actor ? ValidCaptcha(id, ~get("solution")) map { case valid: Boolean =>
        Ok(if (valid) 1 else 0)
      }
    }

  def webmasters =
    Open { implicit ctx =>
      pageHit
      fuccess {
        html.site.help.webmasters()
      }
    }

  def lag =
    Open { implicit ctx =>
      pageHit
      fuccess {
        html.site.lag()
      }
    }

  def ranks =
    Open { implicit ctx =>
      pageHit
      fuccess {
        html.site.documentation.ranks
      }
    }

  def jslog(id: String) =
    Open { ctx =>
      env.round.selfReport(
        userId = ctx.userId,
        ip = HTTPRequest lastRemoteAddress ctx.req,
        fullId = lila.game.Game.FullId(id),
        name = get("n", ctx.req) | "?",
      )
      NoContent.fuccess
    }

  private val JsMonRateLimitPerIp = new lila.memo.RateLimit[lila.common.IpAddress](
    credits = 30,
    duration = 3.minute,
    key = "js.mon.ip",
  )

  def jsmon(event: String) =
    Open { implicit ctx =>
      JsMonRateLimitPerIp(HTTPRequest lastRemoteAddress ctx.req) {
        lila.mon.http.jsmon(event).increment()
        env.report.jsEventsApi.update(event, ctx.userId, get("v"))
        NoContent.fuccess
      }(rateLimitedFu)
    }

  def getJsmon =
    Secure(_.SeeReport) { _ => _ =>
      env.report.jsEventsApi.getRecent dmap { JsonOk(_) }
    }

  // for dev use only
  def imageProxyRedirect(path: String) =
    Action {
      Redirect(s"${env.imgProxyUrl}/$path")
    }

  def signedImage(path: String) =
    Auth { implicit ctx => _ =>
      val url = s"${env.imgProxyUrl}/${lila.common.ImageStorage.Imgproxy.generateSignedPath(
          env.imgProxyKey,
          env.imgProxySalt,
          none,
          path,
        )}"
      negotiate(
        html = Redirect(
          url,
        ).fuccess,
        json = Ok(Json.obj("url" -> url)).fuccess,
      )
    }

  val robots = Action { req =>
    Ok {
      if (env.net.crawlable && req.domain == env.net.domain.value) """User-agent: *
Allow: /
Disallow: /game/export/
Disallow: /games/export/
Disallow: /api/
Allow: /game/export/gif/thumbnail/

User-agent: Twitterbot
Allow: /
"""
      else "User-agent: *\nDisallow: /"
    }
  }

  def manifest =
    Action {
      Ok {
        Json.obj(
          "name"             -> env.net.domain.value,
          "short_name"       -> "Lishogi",
          "start_url"        -> "/",
          "display"          -> "standalone",
          "background_color" -> "#161512",
          "theme_color"      -> "#161512",
          "description"      -> "The (really) free, no-ads, open source shogi server.",
          "icons" -> List(32, 64, 128, 192, 256, 512, 1024).map { size =>
            Json.obj(
              "src"   -> s"//${env.net.assetDomain.value}/assets/logo/lishogi-favicon-$size.png",
              "sizes" -> s"${size}x${size}",
              "type"  -> "image/png",
            )
          },
        )
      } as JSON withHeaders (CACHE_CONTROL -> "max-age=1209600")
    }

  def contact =
    Open { implicit ctx =>
      pageHit
      Ok(html.site.contact()).fuccess
    }

  def faq =
    Open { implicit ctx =>
      pageHit
      Ok(html.site.faq()).fuccess
    }

  def movedPermanently(to: String) =
    Action {
      MovedPermanently(to)
    }

  def devAsset(@nowarn("cat=unused") v: String, path: String, file: String) = assetsC.at(path, file)
}
