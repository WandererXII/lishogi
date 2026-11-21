package views
package html.puzzle

import controllers.routes

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.common.paginator.Paginator
import lila.puzzle.Puzzle
import lila.user.User

object ofPlayer {

  def apply(query: String, user: Option[User], puzzles: Option[Paginator[Puzzle]])(implicit
      ctx: Context,
  ) =
    views.html.base.layout(
      title =
        user.fold(trans.puzzle.lookupOfPlayer.txt())(u => trans.puzzle.fromXGames.txt(u.username)),
      moreCss = cssTag("puzzle.page"),
      moreJs = infiniteScrollTag,
    )(
      main(cls := "page-menu")(
        bits.pageMenu("player"),
        div(cls := "page-menu__content puzzle-of-player box box-pad")(
          form(
            action := routes.Puzzle.ofPlayer(),
            method := "get",
            cls    := "form3 puzzle-of-player__form complete-parent",
          )(
            st.input(
              name         := "name",
              value        := query,
              cls          := "form-control user-autocomplete",
              placeholder  := trans.lishogiUsername.txt(),
              autocomplete := "off",
              dataTag      := "span",
            ),
            submitButton(cls := "button")(trans.puzzle.searchPuzzles.txt()),
          ),
          div(cls := "puzzle-of-player__results")(
            (user, puzzles) match {
              case (Some(usr), Some(pager)) =>
                if (pager.nbResults == 0 && ctx.is(usr))
                  p(
                    "You have no puzzles in the database, but Lishogi still loves you very much.",
                  )
                else
                  frag(
                    p(strong(trans.puzzle.fromXGamesFound((pager.nbResults), showUsername(usr)))),
                    div(cls := "puzzle-of-player__pager infinite-scroll")(
                      pager.currentPageResults.map { puzzle =>
                        div(cls := "puzzle-of-player__puzzle")(
                          views.html.puzzle.bits.miniTag(
                            sfen = puzzle.sfenAfterInitialMove,
                            color = puzzle.color,
                            lastUsi = puzzle.lastUsi,
                          )(
                            a(
                              cls  := s"puzzle-of-player__puzzle__board",
                              href := routes.Puzzle.show(puzzle.id.value),
                            ),
                          ),
                          span(cls := "puzzle-of-player__puzzle__meta")(
                            span(cls := "puzzle-of-player__puzzle__id", s"#${puzzle.id}"),
                            span(cls := "puzzle-of-player__puzzle__rating", puzzle.glicko.intRating),
                          ),
                        )
                      },
                      pagerNext(
                        pager,
                        np => s"${routes.Puzzle.ofPlayer(usr.username.some, np).url}",
                      ),
                    ),
                  )
              case (_, _) =>
                if (query.isEmpty && ctx.isAnon)
                  div(cls := "button-wrap")(
                    a(
                      cls  := "button",
                      href := routes.Auth.signup.url,
                    )(trans.signUp()),
                  )
                else p("User not found")
            },
          ),
        ),
      ),
    )
}
