package views.html.coordinate

import play.api.libs.json.Json

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.common.String.html.safeJsonValue
import lila.pref.Pref.Color
import play.api.i18n.Lang

import controllers.routes

object show {

  def apply(scoreOption: Option[lila.coordinate.Score])(implicit ctx: Context) =
    views.html.base.layout(
      title = trans.coordinates.coordinateTraining.txt(),
      moreCss = cssTag("coordinateTrainer"),
      moreJs = frag(
        jsModule("coordinateTrainer"),
        embedJsUnsafeLoadThen(
          s"""LishogiCoordinateTrainer(document.getElementById('trainer'), ${safeJsonValue(
              bits.coordinateConfig(scoreOption)
            )});"""
        )
      ),
      openGraph = lila.app.ui
        .OpenGraph(
          title = "Shogi board coordinates trainer",
          url = s"$netBaseUrl${routes.Coordinate.home.url}",
          description =
            "Knowing the shogiboard coordinates is a very important shogi skill. A square name appears on the board and you must click on the correct square."
        )
        .some,
      zoomable = true,
      playing = true
    )(
      main(id := "trainer")(
        div(cls   := "trainer")(
          div(cls := "side"),
          div(cls := "main-board")(shogigroundBoard(shogi.variant.Standard, shogi.Sente.some)),
          div(cls := "table"),
          div(cls := "progress")
        )
      )
    )
}
