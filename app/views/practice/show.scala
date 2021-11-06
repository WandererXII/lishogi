package views.html
package practice

import play.api.libs.json.Json

import lishogi.api.Context
import lishogi.app.templating.Environment._
import lishogi.app.ui.ScalatagsTemplate._
import lishogi.common.String.html.safeJsonValue

object show {

  def apply(
      us: lishogi.practice.UserStudy,
      data: lishogi.practice.JsonView.JsData
  )(implicit ctx: Context) =
    views.html.base.layout(
      title = us.practiceStudy.name,
      moreCss = cssTag("analyse.practice"),
      moreJs = frag(
        analyseTag,
        analyseNvuiTag,
        embedJsUnsafe(s"""lishogi=window.lishogi||{};lishogi.practice=${safeJsonValue(
          Json.obj(
            "practice" -> data.practice,
            "study"    -> data.study,
            "data"     -> data.analysis,
            "i18n"     -> board.userAnalysisI18n(),
            "explorer" -> Json.obj(
              "endpoint"          -> explorerEndpoint,
              "tablebaseEndpoint" -> tablebaseEndpoint
            )
          )
        )}""")
      ),
      csp = defaultCsp.withWebAssembly.some,
      shogiground = false,
      zoomable = true
    ) {
      main(cls := "analyse")
    }
}
