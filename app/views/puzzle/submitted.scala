package views
package html.puzzle

import controllers.routes

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.common.paginator.Paginator
import lila.puzzle.Puzzle
import lila.user.User

object submitted {

  def apply(
      query: String,
      user: Option[User],
      puzzles: Option[Paginator[Puzzle]],
      count: Option[Int],
  )(implicit
      ctx: Context,
  ) =
    views.html.base.layout(
      title = s"${trans.puzzle.submissions.txt()}${user.map(u => s" - ${u.username}")}",
      moreCss = cssTag("puzzle.page"),
      moreJs = infiniteScrollTag,
    )(
      main(cls := "page-menu")(
        bits.pageMenu("submitted"),
        div(cls := "page-menu__content puzzle-of-player box box-pad")(
          form(
            action := routes.Puzzle.submitted(),
            method := "get",
            cls    := "form3 puzzle-of-player__form complete-parent",
          )(
            st.input(
              name         := "name",
              value        := query,
              cls          := "form-control user-autocomplete",
              placeholder  := trans.clas.lishogiUsername.txt(),
              autocomplete := "off",
              dataTag      := "span",
            ),
            submitButton(cls := "button")(trans.puzzle.searchPuzzles.txt()),
          ),
          ctx.isAuth option a(
            cls      := "button button-green submit-puzzle",
            dataIcon := Icons.createNew,
            href     := routes.Puzzle.newPuzzlesForm.url,
          ),
          div(cls := "puzzle-of-player__results")(
            (user, puzzles) match {
              case (Some(usr), Some(pager)) =>
                if (pager.nbResults == 0 && ctx.is(usr))
                  p(
                    if (~count > 0)
                      s"Puzzles you have submitted are being verified (${~count} puzzles in queue). Thank you!"
                    else "You have not submitted any puzzles, but you can always change that.",
                  )
                else
                  frag(
                    p(strong(s"Found ${pager.nbResults} puzzles", showUsername(usr))),
                    p(strong(s"Found ${~count} puzzles in queue")),
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
                        np => s"${routes.Puzzle.submitted(usr.username.some, np).url}",
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
