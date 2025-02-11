package views.html
package tournament

import controllers.routes

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._

object faq {

  import trans.arena._

  def page(implicit ctx: Context) =
    views.html.base.layout(
      title = trans.faq.faqAbbreviation.txt(),
      moreCss = cssTag("misc.page"),
    ) {
      main(cls := "page-menu")(
        home.menu("faq"),
        div(cls := "page-menu__content box box-pad")(
          h1(trans.faq.faqAbbreviation()),
          div(cls := "body")(
            div(cls := "arena")(
              apply(lila.tournament.Format.Arena),
            ),
            div(cls := "robin")(
              apply(lila.tournament.Format.Robin),
            ),
          ),
        ),
      )
    }

// TODO
  def apply(
      format: lila.tournament.Format,
      rated: Option[Boolean] = None,
      privateId: Option[String] = None,
  )(implicit
      ctx: Context,
  ) =
    if (format == lila.tournament.Format.Robin)
      frag(
        privateId.map { id =>
          frag(
            h2(trans.arena.thisIsPrivate()),
            p(trans.arena.shareUrl(s"$netBaseUrl${routes.Tournament.show(id)}")), // XXX
          )
        },
        h2(trans.arena.isItRated()),
        rated match {
          case Some(true)  => p(trans.arena.isRated())
          case Some(false) => p(trans.arena.isNotRated())
          case None        => p(trans.arena.someRated())
        },
      )
    else
      frag(
        privateId.map { id =>
          frag(
            h2(trans.arena.thisIsPrivate()),
            p(trans.arena.shareUrl(s"$netBaseUrl${routes.Tournament.show(id)}")), // XXX
          )
        },
        p(trans.arena.willBeNotified()),
        h2(trans.arena.isItRated()),
        rated match {
          case Some(true)  => p(trans.arena.isRated())
          case Some(false) => p(trans.arena.isNotRated())
          case None        => p(trans.arena.someRated())
        },
        h2(howAreScoresCalculated()),
        p(howAreScoresCalculatedAnswer()),
        h2(berserk()),
        p(berserkAnswer()),
        h2(howIsTheWinnerDecided()),
        p(howIsTheWinnerDecidedAnswer()),
        h2(howDoesPairingWork()),
        p(howDoesPairingWorkAnswer()),
        h2(howDoesItEnd()),
        p(howDoesItEndAnswer()),
        h2(otherRules()),
        p(thereIsACountdown()),
        p(drawingWithinNbMoves.pluralSame(10)),
        p(drawStreak(30)),
      )
}
