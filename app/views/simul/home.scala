package views.html.simul

import lishogi.api.Context
import lishogi.app.templating.Environment._
import lishogi.app.ui.ScalatagsTemplate._

import controllers.routes

object home {

  def apply(
      pendings: List[lishogi.simul.Simul],
      opens: List[lishogi.simul.Simul],
      starteds: List[lishogi.simul.Simul],
      finisheds: List[lishogi.simul.Simul]
  )(implicit ctx: Context) =
    views.html.base.layout(
      moreCss = cssTag("simul.list"),
      moreJs = embedJsUnsafe(s"""$$(function() {
  lishogi.StrongSocket.defaults.params.flag = 'simul';
  lishogi.pubsub.on('socket.in.reload', () => {
    $$('.simul-list__content').load('${routes.Simul
        .homeReload()}', () => lishogi.pubsub.emit('content_loaded'));
  });
});"""),
      title = trans.simultaneousExhibitions.txt(),
      openGraph = lishogi.app.ui
        .OpenGraph(
          title = trans.simultaneousExhibitions.txt(),
          url = s"$netBaseUrl${routes.Simul.home()}",
          description = trans.aboutSimul.txt()
        )
        .some
    ) {
      main(cls := "page-menu simul-list")(
        st.aside(cls := "page-menu__menu simul-list__help")(
          p(trans.aboutSimul()),
          img(src := staticUrl("images/fischer-simul.jpg"), alt := "Simul IRL with Bobby Fischer")(
            em("[1964] ", trans.aboutSimulImage()),
            p(trans.aboutSimulRealLife()),
            p(trans.aboutSimulRules()),
            p(trans.aboutSimulSettings())
          )
        ),
        div(cls := "page-menu__content simul-list__content")(
          homeInner(pendings, opens, starteds, finisheds)
        )
      )
    }
}
