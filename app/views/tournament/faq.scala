package views.html
package tournament

import controllers.routes

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._

object faq {

  def page(format: lila.tournament.Format)(implicit ctx: Context) =
    views.html.base.layout(
      title = trans.faq.faqAbbreviation.txt(),
      wrapClass = "full-screen-force",
      moreCss = frag(
        cssTag("misc.page"),
        cssTag("tournament.faq"),
      ),
      moreJs = jsTag("tournament.faq"),
    ) {
      main(cls := "page-menu")(
        home.menu("faq"),
        div(cls := "page-menu__content box box-pad")(
          h1(trans.faq.faqAbbreviation()),
          div(cls := "format-selector")(
            lila.tournament.Format.all.map(f =>
              div(
                cls              := s"format-button${(f.key == format.key) ?? " selected"}",
                attr("data-key") := f.key,
              )(f.trans),
            ),
          ),
          div(cls := "body")(
            lila.tournament.Format.all.map(f =>
              div(cls := s"${f.key}${(f.key != format.key) ?? " none"}")(
                apply(f),
              ),
            ),
          ),
        ),
      )
    }

  def apply(
      format: lila.tournament.Format,
      rated: Option[Boolean] = none,
      maxPlayers: Option[Int] = none,
      privateId: Option[String] = none,
  )(implicit
      ctx: Context,
  ) =
    frag(
      privateId.map { id =>
        frag(
          h3(trans.arena.thisIsPrivate()),
          p(trans.arena.shareUrl(s"$netBaseUrl${routes.Tournament.show(id)}")),
        )
      },
      format match {
        case lila.tournament.Format.Arena     => arena
        case lila.tournament.Format.Robin     => robin
        case lila.tournament.Format.Organized => organized
      },
      h3(trans.arena.howManyPlayersCanJoin()),
      p(
        frag(
          maxPlayers match {
            case Some(num) => trans.arena.howManyPlayersCanJoinAnswer(num)
            case None =>
              trans.arena.howManyPlayersCanJoinAnswerDefault(
                lila.tournament.Format.maxPlayers(format),
              )
          },
          br,
          trans.arena.howManyPlayersCanJoinDenied(),
        ),
      ),
      h3(trans.arena.isItRated()),
      rated match {
        case Some(true)  => p(trans.arena.isRated())
        case Some(false) => p(trans.arena.isNotRated())
        case None        => p(trans.arena.someRated())
      },
    )

  private def arena(implicit
      ctx: Context,
  ) =
    frag(
      h3(trans.arena.howAreScoresCalculated()),
      p(trans.arena.howAreScoresCalculatedAnswer()),
      h3(trans.arena.berserk()),
      p(trans.arena.berserkAnswer()),
      h3(trans.arena.howIsTheWinnerDecided()),
      p(trans.arena.howIsTheWinnerDecidedAnswer()),
      h3(trans.arena.howDoesPairingWork()),
      p(trans.arena.howDoesPairingWorkAnswer()),
      h3(trans.arena.howDoesItEnd()),
      p(trans.arena.howDoesItEndAnswer()),
      h3(trans.arena.otherRules()),
      p(trans.arena.thereIsACountdown()),
      p(trans.arena.drawingWithinNbMoves.pluralSame(10)),
      p(trans.arena.drawStreak(30)),
    )

  private def robin(implicit
      ctx: Context,
  ) = frag(
    h3(trans.tourArrangements.howDoesRoundRobinWork()),
    p(
      trans.tourArrangements.howDoesRoundRobinWorkAnswerNew(),
    ),
    h3(trans.tourArrangements.howToPlayGames()),
    p(trans.tourArrangements.howToPlayGamesAnswerNew()),
    a(href := routes.Article.page("tournament-arrangements"))(
      trans.tourArrangements.howToPlayGames(),
    ),
    h3(trans.tourArrangements.roundRobinScoring()),
    p(trans.tourArrangements.roundRobinScoringAnswer()),
    h3(trans.tourArrangements.roundRobinWinner()),
    p(trans.tourArrangements.roundRobinWinnerAnswer()),
    h3(trans.arena.howDoesItEnd()),
    p(trans.arena.howDoesItEndAnswer()),
  )

  private def organized(implicit
      ctx: Context,
  ) = frag(
    h3(trans.tourArrangements.howDoOrganizedTournamentsWork()),
    p(
      trans.tourArrangements.howDoOrganizedTournamentsWorkAnswerNew(),
    ),
    h3(trans.tourArrangements.whoStartsMatches()),
    p(trans.tourArrangements.whoStartsMatchesAnswerNew()),
    a(href := routes.Article.page("tournament-arrangements"))(
      trans.tourArrangements.howToPlayGames(),
    ),
    h3(trans.tourArrangements.organizedScoring()),
    p(trans.tourArrangements.organizedScoringAnswer()),
    h3(trans.arena.howIsTheWinnerDecided()),
    p(trans.arena.howIsTheWinnerDecidedAnswer()),
    h3(trans.arena.howDoesItEnd()),
    p(trans.arena.howDoesItEndAnswer()),
    h3(trans.tourArrangements.howManyGames()),
    p(
      trans.tourArrangements.howManyGamesAnswer.pluralSame(
        lila.tournament.Format.Organized.maxGames,
      ),
    ),
  )

}
