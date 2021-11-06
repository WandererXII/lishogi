package views.html.analyse

import lishogi.api.Context
import lishogi.app.templating.Environment._
import lishogi.app.ui.ScalatagsTemplate._
import lishogi.game.Pov

object replayBot {

  def apply(
      pov: Pov,
      initialFen: Option[shogi.format.FEN],
      kif: String,
      simul: Option[lishogi.simul.Simul],
      cross: Option[lishogi.game.Crosstable.WithMatchup]
  )(implicit ctx: Context) = {

    views.html.analyse.bits.layout(
      title = replay titleOf pov,
      moreCss = frag(cssTag("analyse.round"), cssTag("analyse.zh")),
      openGraph = povOpenGraph(pov).some
    ) {
      main(cls := "analyse")(
        st.aside(cls := "analyse__side")(
          views.html.game.side(pov, initialFen, none, simul = simul, bookmarked = false)
        ),
        div(cls := "analyse__board main-board")(shogigroundBoard),
        div(cls := "analyse__tools")(div(cls := "ceval")),
        div(cls := "analyse__controls"),
        div(cls := "analyse__underboard")(
          div(cls := "analyse__underboard__panels")(
            div(cls := "fen-notation active")(
              div(
                strong("SFEN"),
                input(readonly, spellcheck := false, cls := "copyable autoselect analyse__underboard__fen")
              ),
              div(cls := "kif")(kif)
            ),
            cross.map { c =>
              div(cls := "ctable active")(
                views.html.game.crosstable(pov.player.userId.fold(c)(c.fromPov), pov.gameId.some)
              )
            }
          )
        )
      )
    }
  }
}
