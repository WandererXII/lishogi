package views.html
package user

import controllers.routes

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.user.User

object top {

  def apply(perfType: lila.rating.PerfType, users: List[User.LightPerf])(implicit ctx: Context) = {

    val title = s"${perfType.trans} top 200"

    views.html.base.layout(
      title = title,
      moreCss = cssTag("misc.slist"),
      openGraph = lila.app.ui
        .OpenGraph(
          title = s"${trans.leaderboard.txt()} - ${perfType.trans}",
          url = s"$netBaseUrl${routes.User.topNb(200, perfType.key).url}",
          description = trans.topXPlayersInY.txt("200", perfType.trans),
        )
        .some,
    )(
      main(cls := "page-small box")(
        h1(a(href := routes.User.list, dataIcon := "I"), title),
        table(cls := "slist slist-pad")(
          tbody(
            users.zipWithIndex.map { case (u, i) =>
              tr(
                td(i + 1),
                td(lightUserLink(u.user)),
                td(u.rating),
                td(ratingProgress(u.progress)),
              )
            },
          ),
        ),
      ),
    )
  }

}
