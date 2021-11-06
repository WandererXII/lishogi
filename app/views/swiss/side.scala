package views
package html.swiss

import lishogi.api.Context
import lishogi.app.templating.Environment._
import lishogi.app.ui.ScalatagsTemplate._
import lishogi.common.String.html.richText
import lishogi.swiss.Swiss

import controllers.routes

object side {

  private val separator = " • "

  def apply(s: Swiss, chat: Boolean)(implicit ctx: Context) =
    frag(
      div(cls := "swiss__meta")(
        st.section(dataIcon := s.perfType.map(_.iconChar.toString))(
          div(
            p(
              s.clock.show,
              separator,
              if (s.variant.exotic) {
                views.html.game.bits.variantLink(
                  s.variant,
                  s.variant.name
                )
              } else s.perfType.map(_.trans),
              separator,
              if (s.settings.rated) trans.ratedTournament() else trans.casualTournament()
            ),
            p(
              span(cls := "swiss__meta__round")(s"${s.round}/${s.settings.nbRounds}"),
              " rounds",
              separator,
              a(href := routes.Page.notSupported())("Swiss"),
              (isGranted(_.ManageTournament) || (ctx.userId.has(s.createdBy) && !s.isFinished)) option frag(
                " ",
                a(href := routes.Page.notSupported(), title := "Edit tournament")(iconTag("%"))
              )
            ),
            bits.showInterval(s)
          )
        ),
        s.settings.description map { d =>
          st.section(cls := "description")(richText(d))
        },
        s.looksLikePrize option views.html.tournament.bits.userPrizeDisclaimer,
        teamLink(s.teamId),
        separator,
        absClientDateTime(s.startsAt)
      ),
      chat option views.html.chat.frag
    )
}
