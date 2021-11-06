package views.html.base

import lishogi.common.String.html.safeJsonValue
import play.api.libs.json.Json
import scala.language.reflectiveCalls

import lishogi.api.Context
import lishogi.app.templating.Environment._
import lishogi.app.ui.ScalatagsTemplate._

import controllers.routes

object captcha {

  private val dataCheckUrl = attr("data-check-url")
  private val dataPlayable = attr("data-playable")
  private val dataX        = attr("data-x")
  private val dataY        = attr("data-y")
  private val dataZ        = attr("data-z")

  def apply(form: lishogi.common.Form.FormLike, captcha: lishogi.common.Captcha)(implicit ctx: Context) =
    frag(
      form3.hidden(form("gameId"), captcha.gameId.some),
      if (ctx.blind) form3.hidden(form("move"), captcha.solutions.head.some)
      else {
        val url = netBaseUrl + routes.Round.watcher(captcha.gameId, if (captcha.sente) "sente" else "gote")
        div(
          cls := List(
            "captcha form-group" -> true,
            "is-invalid"         -> lishogi.common.Captcha.isFailed(form)
          ),
          dataCheckUrl := routes.Main.captchaCheck(captcha.gameId)
        )(
          div(cls := "challenge")(
            div(
              cls := "mini-board cg-wrap parse-fen is2d",
              dataPlayable := "1",
              dataX := encodeFen(safeJsonValue(Json.toJson(captcha.moves))),
              dataY := encodeFen(if (captcha.sente) {
                "sente"
              } else {
                "gote"
              }),
              dataZ := encodeFen(captcha.fen)
            )(cgWrapContent)
          ),
          div(cls := "captcha-explanation")(
            label(cls := "form-label")(
              if (captcha.sente) trans.blackCheckmatesInOneMove()
              else trans.whiteCheckmatesInOneMove()
            ),
            br,
            br,
            trans.thisIsAChessCaptcha(),
            br,
            trans.clickOnTheBoardToMakeYourMove(),
            br,
            br,
            trans.help(),
            " ",
            a(title := trans.viewTheSolution.txt(), target := "_blank", href := s"${url}#last")(url),
            div(cls := "result success text", dataIcon := "E")(trans.checkmate()),
            div(cls := "result failure text", dataIcon := "k")(trans.notACheckmate()),
            form3.hidden(form("move"))
          )
        )
      }
    )
}
