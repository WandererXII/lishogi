package lila.app
package http

import scala.concurrent.Future
import scala.concurrent.duration._

import play.api.Configuration
import play.api.Environment
import play.api.Mode
import play.api.UsefulException
import play.api.http.DefaultHttpErrorHandler
import play.api.mvc.Results._
import play.api.mvc._
import play.api.routing._
import play.core.SourceMapper

import lila.common.HTTPRequest

final class ErrorHandler(
    environment: Environment,
    config: Configuration,
    sourceMapper: Option[SourceMapper],
    router: => Router,
    mainC: => controllers.Main,
    lobbyC: => controllers.Lobby,
)(implicit ec: scala.concurrent.ExecutionContext)
    extends DefaultHttpErrorHandler(environment, config, sourceMapper, router.some) {

  override def logServerError(req: RequestHeader, exception: UsefulException): Unit =
    if (environment.mode != Mode.Prod) super.logServerError(req, exception)

  private val logServiceUnavailable = lila.memo.OnceEvery(10.seconds)

  private def serviceUnavailable(e: Throwable): Option[lila.base.LilaServiceUnavailable] =
    Iterator
      .iterate(e)(_.getCause)
      .takeWhile(_ != null)
      .take(10)
      .collectFirst { case u: lila.base.LilaServiceUnavailable => u }

  override def onProdServerError(req: RequestHeader, exception: UsefulException) =
    serviceUnavailable(exception).fold(onInternalError(req, exception))(
      onServiceUnavailable(req, _),
    )

  private def onServiceUnavailable(
      req: RequestHeader,
      e: lila.base.LilaServiceUnavailable,
  ): Fu[Result] = {
    val actionName = HTTPRequest actionName req
    val client     = HTTPRequest clientName req
    lila.mon.http.error(actionName, client, req.method, 503).increment()
    if (logServiceUnavailable(e.service))
      lila
        .log("http")
        .warn(s"ERROR 503 $actionName ${e.message} (muting ${e.service} errors for 10s)")
    Future {
      if (canShowErrorPage(req)) ServiceUnavailable(errorPage(req))
      else ServiceUnavailable("Sorry, this service is temporarily unavailable.")
    } recover { case util.control.NonFatal(err) =>
      lila.log("http").error(s"""Error handler exception on "${e.message}\"""", err)
      ServiceUnavailable("Sorry, this service is temporarily unavailable.")
    }
  }

  private def onInternalError(req: RequestHeader, exception: UsefulException) =
    Future {
      val actionName = HTTPRequest actionName req
      val client     = HTTPRequest clientName req
      lila.mon.http.error(actionName, client, req.method, 500).increment()
      lila.log("http").error(s"ERROR 500 $actionName", exception)
      if (canShowErrorPage(req)) InternalServerError(errorPage(req))
      else InternalServerError("Sorry, something went wrong.")
    } recover { case util.control.NonFatal(e) =>
      lila.log("http").error(s"""Error handler exception on "${exception.getMessage}\"""", e)
      InternalServerError("Sorry, something went very wrong.")
    }

  override def onClientError(req: RequestHeader, statusCode: Int, msg: String): Fu[Result] =
    statusCode match {
      case 404 if canShowErrorPage(req) => mainC.handlerNotFound(req)
      case 404                          => fuccess(NotFound("404 - Resource not found"))
      case 403                          => lobbyC.handleStatus(req, Results.Forbidden)
      case _ if req.attrs.contains(request.RequestAttrKey.Session) =>
        lobbyC.handleStatus(req, Results.BadRequest)
      case _ =>
        fuccess {
          Results.BadRequest("Sorry, the request could not be processed")
        }
    }

  private def errorPage(req: RequestHeader) =
    views.html.site.bits.errorPage {
      lila.api.Context.error(
        req,
        lila.i18n.defaultLang,
        HTTPRequest.isSynchronousHttp(req) option lila.common.Nonce.random,
      )
    }

  private def canShowErrorPage(req: RequestHeader): Boolean =
    HTTPRequest.isSynchronousHttp(req) && !HTTPRequest.hasFileExtension(req)
}
