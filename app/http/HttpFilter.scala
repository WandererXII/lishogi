package lila.app
package http

import play.api.mvc._

import akka.stream.Materializer

import lila.common.HTTPRequest

final class HttpFilter(env: Env)(implicit val mat: Materializer) extends Filter {

  private val httpMon     = lila.mon.http
  private val net         = env.net
  private val logger      = lila.log("http")
  private val logRequests = env.config.get[Boolean]("net.http.log")

  def apply(nextFilter: RequestHeader => Fu[Result])(req: RequestHeader): Fu[Result] =
    if (HTTPRequest isAssets req) nextFilter(req) dmap { result =>
      result.withHeaders(
        "Service-Worker-Allowed"       -> "/",
        "Cross-Origin-Embedder-Policy" -> "require-corp",
      )
    }
    else {
      val startTime = nowMillis
      redirectWrongDomain(req) map fuccess getOrElse {
        nextFilter(req) dmap finalizeResponse(req) dmap { result =>
          monitoring(req, startTime, result)
          result
        }
      }
    }

  private val ignoredLogUris = List(
    "/.well-known/appspecific/com.chrome.devtools.json",
  )

  private def monitoring(req: RequestHeader, startTime: Long, result: Result) = {
    val actionName = HTTPRequest actionName req
    val reqTime    = nowMillis - startTime
    val statusCode = result.header.status
    val client     = HTTPRequest clientName req
    if (env.isDev) {
      if (logRequests && !ignoredLogUris.contains(req.uri))
        logger.info(
          s"[$statusCode] ${s"[$client]".padTo(9, ' ')} ${req.method.padTo(6, ' ')} ${req.uri} -> $actionName (${reqTime}ms)",
        )
    } else httpMon.time(actionName, client, req.method, statusCode).record(reqTime)
  }

  private def redirectWrongDomain(req: RequestHeader): Option[Result] =
    (
      req.host != net.domain.value &&
        HTTPRequest.isRedirectable(req) &&
        !HTTPRequest.isProgrammatic(req) &&
        // asset request going through the CDN, don't redirect
        !(req.host == net.assetDomain.value && HTTPRequest.hasFileExtension(req))
    ) option Results.MovedPermanently(
      s"http${if (req.secure) "s" else ""}://${net.domain}${req.uri}",
    )

  private def finalizeResponse(req: RequestHeader)(result: Result) =
    if (HTTPRequest.isApiOrApp(req))
      result.withHeaders(ResponseHeaders.headersForApiOrApp(req): _*)
    else if (
      HTTPRequest
        .userSessionId(req)
        .isEmpty && result.session(req).get("lang").isEmpty &&
      HTTPRequest.isHuman(req)
    )
      req
        .getQueryString("lang")
        .flatMap(lila.i18n.I18nLangPicker.byQuery)
        .fold(result) { lang =>
          result.withCookies(env.lilaCookie.session("lang", lang.code)(req))
        }
    else
      result
}
