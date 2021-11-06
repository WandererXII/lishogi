package controllers

import scala.annotation.nowarn

import lishogi.app._
import lishogi.app.http.ResponseHeaders.allowMethods
import lishogi.common.HTTPRequest.isApiOrApp

final class Options(env: Env) extends LishogiController(env) {

  val root = all("")

  def all(@nowarn("cat=unused") url: String) =
    Action { req =>
      if (isApiOrApp(req)) apiHeaders
      else NotFound
    }

  private val apiHeaders = NoContent.withHeaders(
    "Allow"                  -> allowMethods,
    "Access-Control-Max-Age" -> "1728000"
  )
}
