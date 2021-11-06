package views.html
package user

import lishogi.api.Context
import lishogi.app.templating.Environment._
import lishogi.app.ui.ScalatagsTemplate._
import lishogi.user.User

import controllers.routes

object top {

  def apply(perfType: lishogi.rating.PerfType, users: List[User.LightPerf])(implicit ctx: Context) = {

    val title = s"${perfType.trans} top 200"

    views.html.base.layout(
      title = title,
      moreCss = cssTag("slist"),
      openGraph = lishogi.app.ui
        .OpenGraph(
          title = s"Leaderboard of ${perfType.trans}",
          url = s"$netBaseUrl${routes.User.topNb(200, perfType.key).url}",
          description = s"The 200 best shogi players in ${perfType.trans}, sorted by rating"
        )
        .some
    )(
      main(cls := "page-small box")(
        h1(a(href := routes.User.list(), dataIcon := "I"), title),
        table(cls := "slist slist-pad")(
          tbody(
            users.zipWithIndex.map { case (u, i) =>
              tr(
                td(i + 1),
                td(lightUserLink(u.user)),
                td(u.rating),
                td(ratingProgress(u.progress))
              )
            }
          )
        )
      )
    )
  }

}
