package views
package html.puzzle

import controllers.routes
import play.api.i18n.Lang

import lishogi.api.Context
import lishogi.app.templating.Environment._
import lishogi.app.ui.ScalatagsTemplate._
import lishogi.common.paginator.Paginator
import lishogi.puzzle.PuzzleHistory.{ PuzzleSession, SessionRound }
import lishogi.puzzle.{ PuzzleRound, PuzzleTheme }
import lishogi.user.User

object history {

  def apply(user: User, page: Int, pager: Paginator[PuzzleSession])(implicit ctx: Context) =
    views.html.base.layout(
      title = "Puzzle history",
      moreCss = cssTag("puzzle.dashboard"),
      moreJs = infiniteScrollTag
    )(
      main(cls := "page-menu")(
        bits.pageMenu("history"),
        div(cls := "page-menu__content box box-pad")(
          h1(trans.puzzle.history()),
          div(cls := "puzzle-history")(
            div(cls := "infinite-scroll")(
              pager.currentPageResults map renderSession,
              pagerNext(pager, np => s"${routes.Puzzle.history(np).url}${!ctx.is(user) ?? s"&u=${user.id}"}")
            )
          )
        )
      )
    )

  private def renderSession(session: PuzzleSession)(implicit ctx: Context) =
    div(cls := "puzzle-history__session")(
      h2(cls := "puzzle-history__session__title")(
        strong(PuzzleTheme(session.theme).name()),
        momentFromNow(session.puzzles.head.round.date)
      ),
      div(cls := "puzzle-history__session__rounds")(session.puzzles.toList.reverse map renderRound)
    )

  private def renderRound(r: SessionRound)(implicit ctx: Context) =
    a(cls := "puzzle-history__round", href := routes.Puzzle.show(r.puzzle.id.value))(
      views.html.game.bits.miniTag(r.puzzle.fenAfterInitialMove, r.puzzle.color, r.puzzle.lastMove)(
        span(cls := "puzzle-history__round__puzzle")
      ),
      span(cls := "puzzle-history__round__meta")(
        span(cls := "puzzle-history__round__result")(
          if (r.round.win) goodTag(trans.puzzle.solved())
          else badTag(trans.puzzle.failed())
        ),
        span(cls := "puzzle-history__round__id")(s"#${r.puzzle.id.value}")
      )
    )
}
