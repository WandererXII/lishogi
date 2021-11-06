package views.html.tv

import lishogi.api.Context
import lishogi.app.templating.Environment._
import lishogi.app.ui.ScalatagsTemplate._

import controllers.routes

object side {

  def channels(
      channel: lishogi.tv.Tv.Channel,
      champions: lishogi.tv.Tv.Champions,
      baseUrl: String
  ): Frag =
    div(cls := "tv-channels subnav")(
      lishogi.tv.Tv.Channel.all.map { c =>
        a(
          href := s"$baseUrl/${c.key}",
          cls := List(
            "tv-channel" -> true,
            c.key        -> true,
            "active"     -> (c == channel)
          )
        )(
          span(dataIcon := c.icon)(
            span(
              strong(c.name),
              span(cls := "champion")(
                champions.get(c).fold[Frag](raw(" - ")) { p =>
                  frag(
                    p.user.title.fold[Frag](p.user.name)(t => frag(t, nbsp, p.user.name)),
                    " ",
                    p.rating
                  )
                }
              )
            )
          )
        )
      }
    )

  private val separator = " • "

  def meta(pov: lishogi.game.Pov)(implicit ctx: Context): Frag = {
    import pov._
    div(cls := "game__meta")(
      st.section(
        div(cls := "game__meta__infos", dataIcon := views.html.game.bits.gameIcon(game))(
          div(cls := "header")(
            div(cls := "setup")(
              views.html.game.widgets showClock game,
              separator,
              (if (game.rated) trans.rated else trans.casual).txt(),
              separator,
              if (game.variant.exotic)
                views.html.game.bits.variantLink(
                  game.variant,
                  (game.variant.name).toUpperCase
                )
              else
                game.perfType.map { pt =>
                  span(title := pt.desc)(pt.trans)
                }
            )
          )
        ),
        div(cls := "game__meta__players")(
          game.players.map { p =>
            div(cls := s"player color-icon is ${p.color.name} text")(
              playerLink(p, withOnline = false, withDiff = true, withBerserk = true)
            )
          }
        )
      ),
      game.tournamentId map { tourId =>
        st.section(cls := "game__tournament-link")(
          a(href := routes.Tournament.show(tourId), dataIcon := "g", cls := "text")(
            tournamentIdToName(tourId)
          )
        )
      }
    )
  }

  def sides(
      pov: lishogi.game.Pov,
      cross: Option[lishogi.game.Crosstable.WithMatchup]
  )(implicit ctx: Context) =
    div(cls := "sides")(
      cross.map {
        views.html.game.crosstable(_, pov.gameId.some)
      }
    )
}
