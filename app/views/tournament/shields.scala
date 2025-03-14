package views.html.tournament

import controllers.routes

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.tournament.TournamentShield

object shields {

  private val section = st.section(cls := "tournament-shields__item")

  def apply(history: TournamentShield.History)(implicit ctx: Context) =
    views.html.base.layout(
      title = trans.tournamentShields.txt(),
      moreCss = cssTag("tournament.leaderboard"),
      wrapClass = "full-screen-force",
    ) {
      main(cls := "page-menu")(
        views.html.user.bits.communityMenu("shield"),
        div(cls := "page-menu__content box box-pad")(
          h1(trans.tournamentShields()),
          div(cls := "tournament-shields")(
            history.sorted.map {
              case (categ, awards) => {
                section(
                  h2(
                    a(href := routes.Tournament.categShields(categ.key))(
                      span(cls := "shield-trophy")(categ.iconChar.toString),
                      categ.trans,
                    ),
                  ),
                  ol(awards.map { aw =>
                    li(
                      userIdLink(aw.owner.value.some),
                      a(href := routes.Tournament.show(aw.tourId))(showDate(aw.date)),
                    )
                  }),
                )
              }
            },
          ),
        ),
      )
    }

  def byCateg(categ: TournamentShield.Category, awards: List[TournamentShield.Award])(implicit
      ctx: Context,
  ) =
    views.html.base.layout(
      title = trans.tournamentShields.txt(),
      moreCss = frag(cssTag("tournament.leaderboard"), cssTag("misc.slist")),
    ) {
      main(cls := "page-menu page-small tournament-categ-shields")(
        views.html.user.bits.communityMenu("shield"),
        div(cls := "page-menu__content box")(
          h1(
            a(href := routes.Tournament.shields, dataIcon := "I", cls := "text"),
            categ.trans,
            " shields",
          ),
          ol(awards.map { aw =>
            li(
              span(cls := "shield-trophy")(categ.iconChar.toString),
              userIdLink(aw.owner.value.some),
              a(href := routes.Tournament.show(aw.tourId))(showDate(aw.date)),
            )
          }),
        ),
      )
    }
}
