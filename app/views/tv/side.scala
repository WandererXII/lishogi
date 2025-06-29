package views.html.tv

import play.api.i18n.Lang

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._

object side {

  def channels(
      channel: lila.tv.Tv.Channel,
      champions: lila.tv.Tv.Champions,
      baseUrl: String,
  )(implicit lang: Lang): Frag =
    div(cls := "tv-channels subnav")(
      lila.tv.Tv.Channel.all.map { c =>
        a(
          href := s"$baseUrl/${c.key}",
          cls := List(
            "tv-channel" -> true,
            c.key        -> true,
            "active"     -> (c == channel),
          ),
        )(
          span(dataIcon := c.icon)(
            span(
              strong(transKeyTxt(c.key)),
              span(cls := "champion")(
                champions.get(c).fold[Frag](raw(" - ")) { p =>
                  frag(
                    p.user.title.fold[Frag](p.user.name)(t => frag(t, nbsp, p.user.name)),
                    " ",
                    p.rating,
                  )
                },
              ),
            ),
          ),
        )
      },
    )

  private val separator = " - "

  def meta(pov: lila.game.Pov)(implicit ctx: Context): Frag = {
    import pov.game
    div(cls := "game__meta")(
      st.section(
        div(cls := "game__meta__infos", dataIcon := views.html.game.bits.gameIcon(game))(
          div(cls := "header")(
            div(cls := "setup")(
              views.html.game.widgets showClock game,
              separator,
              (if (game.rated) trans.rated else trans.casual).txt(),
              separator,
              views.html.game.bits.variantLink(game.variant, game.perfType),
            ),
          ),
        ),
        div(cls := s"game__meta__players orientation-${pov.color.name}")(
          game.players.map { p =>
            div(cls := s"player color-icon is ${p.color.name} text")(
              playerLink(p, withOnline = false, withDiff = true, withBerserk = true),
            )
          },
        ),
      ),
      game.tournamentId map { tourId =>
        st.section(cls := "game__tournament-link")(tournamentLink(tourId))
      },
    )
  }

  def sides(
      pov: lila.game.Pov,
      cross: Option[lila.game.Crosstable.WithMatchup],
  )(implicit ctx: Context) =
    div(cls := "sides")(
      cross.map {
        views.html.game.crosstable(_, pov.gameId.some)
      },
    )
}
