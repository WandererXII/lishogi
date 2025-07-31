package views.html
package game

import controllers.routes

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._

object side {

  private val separator  = " - "
  private val dataUserTv = attr("data-user-tv")
  private val dataTime   = attr("data-time")

  def apply(
      pov: lila.game.Pov,
      tour: Option[lila.tournament.TourAndTeamVs],
      simul: Option[lila.simul.Simul],
      userTv: Option[lila.user.User] = None,
      backToGame: Option[lila.game.Player] = None,
      bookmarked: Boolean,
  )(implicit ctx: Context): Option[Frag] =
    ctx.noBlind option frag(
      meta(pov, tour, simul, userTv, backToGame, bookmarked),
      pov.game.userIds.filter(isStreaming) map views.html.streamer.bits.contextual,
    )

  def meta(
      pov: lila.game.Pov,
      tour: Option[lila.tournament.TourAndTeamVs],
      simul: Option[lila.simul.Simul],
      userTv: Option[lila.user.User] = None,
      backToGame: Option[lila.game.Player] = None,
      bookmarked: Boolean,
  )(implicit ctx: Context): Option[Frag] =
    ctx.noBlind option {
      import pov.game

      val tourOrSimulLink = tour map { t =>
        a(
          dataIcon := "g",
          cls      := "text button button-empty",
          href     := routes.Tournament.show(t.tour.id).url,
        )(t.tour.trans)
      } orElse game.tournamentId.map { tourId =>
        a(
          dataIcon := "g",
          cls      := "text button button-empty",
          href     := routes.Tournament.show(tourId).url,
        )(tournamentIdToName(tourId))
      } orElse simul.map { sim =>
        a(dataIcon := "r", cls := "text button button-empty", href := routes.Simul.show(sim.id))(
          sim.name,
        )
      }

      div(cls := "game__meta")(
        st.section(
          div(cls := "game__meta__infos", dataIcon := bits.gameIcon(game))(
            div(
              div(cls := "header")(
                div(cls := "setup")(
                  views.html.bookmark.toggle(game, bookmarked),
                  if (game.imported)
                    div(
                      a(href := routes.Importer.importGame, title := trans.importGame.txt())(
                        trans.importGame(),
                      ),
                    )
                  else
                    frag(
                      widgets showClock game,
                      separator,
                      (if (game.rated) trans.rated else trans.casual).txt(),
                      separator,
                      bits.variantLink(game.variant),
                      game.isProMode option frag(
                        br,
                        small(cls := "text", dataIcon := "8")(trans.proMode()),
                      ),
                    ),
                ),
                game.notationImport.flatMap(_.date).map(frag(_)) getOrElse {
                  frag(
                    if (game.isBeingPlayed) trans.playingRightNow()
                    else if (game.paused) trans.gameAdjourned()
                    else momentFromNow(game.createdAt),
                  )
                },
              ),
              game.notationImport.flatMap(_.user).map { importedBy =>
                small(
                  trans.importedByX(userIdLink(importedBy.some, None, false)),
                )
              },
            ),
          ),
          div(cls := s"game__meta__players orientation-${pov.color.name}")(
            game.players.map { p =>
              frag(
                div(cls := s"player color-icon is ${p.color.name} text")(
                  playerLink(p, withOnline = false, withDiff = true, withBerserk = true),
                  tour.flatMap(_.teamVs).map(_.teams(p.color)) map {
                    teamLink(_, false)(cls := "team")
                  },
                ),
              )
            },
          ),
        ),
        game.finishedOrAborted option {
          st.section(cls := "status")(
            gameEndStatus(game),
            game.winnerColor.map { color =>
              frag(
                separator,
                transWithColorName(trans.xIsVictorious, color, game.isHandicap),
              )
            },
          )
        },
        userTv.map { u =>
          st.section(cls := "game__tv")(
            h2(cls := "top user-tv text", dataUserTv := u.id, dataIcon := "1")(u.titleUsername),
          )
        },
        tour ?? { t =>
          t.tour.isArena option
            st.section(cls := "game__tournament-clock")(
              div(cls := "clock", dataTime := t.tour.secondsToFinish)(
                div(cls := "time")(t.tour.clockStatus),
              ),
            )
        },
        (backToGame.isDefined || tourOrSimulLink.isDefined) option
          st.section(cls := "game__links")(
            frag(
              backToGame.flatMap(game.fullIdOf) map { fullId =>
                a(
                  cls      := s"button button-empty${tourOrSimulLink.isEmpty ?? " text"}",
                  dataIcon := "i",
                  href     := routes.Round.player(fullId),
                  title    := trans.backToGame.txt(),
                )(
                  tourOrSimulLink.isEmpty option trans.backToGame(),
                )
              },
              tourOrSimulLink,
            ),
          ),
      )
    }
}
