package views.html
package tournament.user

import controllers.routes

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.common.paginator.Paginator
import lila.user.User

object bits {

  def best(query: String, user: User, pager: Paginator[lila.tournament.LeaderboardApi.TourEntry])(
      implicit ctx: Context,
  ) =
    layout(
      query = query,
      userOpt = user.some,
      path = "best",
      moreJs = infiniteScrollTag,
    ) {
      views.html.tournament.user.list(user, "best", pager, iconTag(Icons.trophy))
    }

  def recent(query: String, user: User, pager: Paginator[lila.tournament.LeaderboardApi.TourEntry])(
      implicit ctx: Context,
  ) =
    layout(
      query = query,
      userOpt = user.some,
      path = "recent",
      moreJs = infiniteScrollTag,
    ) {
      views.html.tournament.user.list(user, "recent", pager, pager.nbResults.toString)
    }

  def empty(query: String, path: String)(implicit ctx: Context) = layout(
    query,
    none,
    path,
  )(
    div(cls := "notours")(
      if (query.isEmpty && ctx.isAnon)
        div(cls := "button-wrap")(
          a(
            cls  := "button",
            href := routes.Auth.signup.url,
          )(trans.signUp()),
        )
      else "User not found",
    ),
  )

  def layout(query: String, userOpt: Option[User], path: String, moreJs: Frag = emptyFrag)(
      body: Frag,
  )(implicit ctx: Context) = {
    val paths = List(
      ("created", trans.tourCreated.txt()),
      ("recent", trans.tourRecent.txt()),
      ("best", trans.bestResults.txt()),
      ("chart", trans.stats.txt()),
      ("upcoming", trans.tourUpcoming.txt()),
    )
    val curPath = paths.find(_._1 == path)
    views.html.base.layout(
      title = (for {
        u <- userOpt
        p <- curPath
      } yield s"${u.username} - ${p._2}").getOrElse(trans.pageNotFound.txt()),
      wrapClass = "full-screen-force",
      moreCss = cssTag("tournament.user"),
      moreJs = moreJs,
    ) {
      main(cls := "page-menu")(
        views.html.tournament.home.menu("user"),
        div(cls := "page-menu__content box")(
          form(
            action := routes.UserTournament.ofPlayer(path),
            method := "get",
            cls    := "form3 complete-parent",
          )(
            st.input(
              name         := "name",
              value        := query,
              cls          := "form-control user-autocomplete",
              placeholder  := trans.lishogiUsername.txt(),
              autocomplete := "off",
              dataTag      := "span",
            ),
            submitButton(cls := s"button${(path == "upcoming") ?? " disabled"}")(
              trans.study.searchByUsername(),
            ),
          ),
          div(cls := "angle-content")(
            div(cls := "number-menu number-menu--tabs menu-box-pop")(
              paths.map { ps =>
                a(
                  cls := List(
                    s"nm-item to-${ps._1}" -> true,
                    "active"               -> (ps._1 == path),
                    "none" -> (!userOpt.exists(u => ctx.is(u)) && ps._1 == "upcoming"),
                  ),
                  href := routes.UserTournament
                    .ofPlayer(ps._1, userOpt.map(_.username).orElse(query.some).filter(_.nonEmpty)),
                )(ps._2)
              },
            ),
            body,
          ),
        ),
      )
    }
  }
}
