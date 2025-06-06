package views.html.simul

import controllers.routes
import play.api.libs.json.Json

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._

object home {

  def apply(
      pendings: List[lila.simul.Simul],
      opens: List[lila.simul.Simul],
      starteds: List[lila.simul.Simul],
      finisheds: List[lila.simul.Simul],
  )(implicit ctx: Context) =
    views.html.base.layout(
      moreCss = cssTag("simul.list"),
      moreJs = moduleJsTag("simul.home", Json.obj()),
      title = trans.simultaneousExhibitions.txt(),
      openGraph = lila.app.ui
        .OpenGraph(
          title = trans.simultaneousExhibitions.txt(),
          url = s"$netBaseUrl${routes.Simul.home}",
          description = trans.aboutSimul.txt(),
        )
        .some,
      withHrefLangs = lila.i18n.LangList.All.some,
    ) {
      main(cls := "page-menu simul-list")(
        st.aside(cls := "page-menu__menu simul-list__help")(
          p(trans.aboutSimul()),
          img(
            src := staticUrl("images/fischer-simul.jpg"),
            alt := "Simul IRL with Bobby Fischer",
          )(
            em("[1964] ", trans.aboutSimulImage()),
            p(trans.aboutSimulRealLife()),
            p(trans.aboutSimulRules()),
            p(trans.aboutSimulSettings()),
          ),
        ),
        div(cls := "page-menu__content simul-list__content")(
          homeInner(pendings, opens, starteds, finisheds),
        ),
      )
    }
}
