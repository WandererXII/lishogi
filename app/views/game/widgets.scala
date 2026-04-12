package views.html
package game

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.game.Game
import lila.game.Player
import lila.game.Pov

object widgets {

  private val separator = " - "

  def apply(
      games: Seq[Game],
      user: Option[lila.user.User] = None,
  )(implicit ctx: Context): Frag =
    games map { g =>
      val fromPlayer  = user flatMap g.player
      val firstPlayer = fromPlayer | g.firstPlayer
      st.article(cls := "game-row paginated")(
        a(cls := "game-row__overlay", href := gameLink(g, firstPlayer.color)),
        div(cls := "game-row__board")(
          gameSfen(Pov(g, firstPlayer), withLink = false, withTitle = false),
        ),
        div(cls := "game-row__infos")(
          div(cls := "header", dataIcon := bits.gameIcon(g))(
            div(cls := "header__text")(
              strong(
                if (g.imported)
                  frag(
                    g.notationImport.flatMap(_.user) map { uid =>
                      trans.importedByX(showUsernameById(uid.some, None, false))
                    } getOrElse span("IMPORT"),
                    separator,
                    bits.variantLink(g.variant, none),
                  )
                else
                  frag(
                    showClock(g),
                    separator,
                    g.perfType.trans,
                    separator,
                    if (g.rated) trans.rated.txt() else trans.casual.txt(),
                  ),
              ),
              g.notationImport.flatMap(_.date).fold(momentFromNow(g.createdAt))(frag(_)),
              g.tournamentId.map { tourId =>
                frag(separator, tournamentLink(tourId))
              } orElse
                g.simulId.map { simulId =>
                  frag(separator, views.html.simul.bits.link(simulId))
                },
            ),
          ),
          div(cls := "header__center")(
            div(cls := "versus")(
              gamePlayer(g.sentePlayer),
              div(cls := "swords", dataIcon := Icons.challenge),
              gamePlayer(g.gotePlayer),
            ),
            div(cls := "result")(
              if (g.isBeingPlayed) trans.playingRightNow()
              else if (g.paused) trans.gameAdjourned()
              else {
                if (g.finishedOrAborted)
                  span(
                    cls := g.winner.flatMap(w => fromPlayer.map(p => if (p == w) "win" else "loss")),
                  )(
                    gameEndStatus(g),
                    g.winner.map { winner =>
                      frag(
                        ", ",
                        transWithColorName(trans.xIsVictorious, winner.color, g.isHandicap),
                      )
                    },
                  )
                else transWithColorName(trans.xPlays, g.turnColor, g.isHandicap)
              },
            ),
          ),
          div(
            if (g.playedPlies > 0)
              div(cls := "moves-count")(
                span(trans.nbMoves.pluralSame(g.playedPlies)),
              )
            else frag(br, br),
            g.metadata.analysed option
              div(cls := "metadata text", dataIcon := Icons.barChart)(
                trans.computerAnalysisAvailable(),
              ),
          ),
        ),
      )
    }

  def showClock(game: Game)(implicit ctx: Context) =
    game.clock.map { clock =>
      frag(clock.config.show)
    } getOrElse {
      game.daysPerTurn
        .map { days =>
          span(title := trans.correspondence.txt())(
            if (days == 1) trans.oneDay()
            else trans.nbDays.pluralSame(days),
          )
        }
        .getOrElse {
          span(title := trans.unlimited.txt())("∞")
        }
    }

  private def gamePlayer(player: Player)(implicit ctx: Context) =
    div(cls := s"player ${player.color.name}")(
      player.playerUser map { playerUser =>
        frag(
          showUsernameById(
            playerUser.id.some,
            rating = player.stableRating,
            withOnline = false,
          ),
          br,
          player.berserk option berserkIconSpan,
          playerUser.rating,
          player.provisional option "?",
          playerUser.ratingDiff map { d =>
            frag(" ", showRatingDiff(d))
          },
        )
      } getOrElse {
        player.engineConfig map { ec =>
          engineSpan(ec)
        } getOrElse {
          (player.nameSplit.fold[Frag](anonSpan) { case (name, rating) =>
            frag(
              span(name),
              rating.map { r =>
                frag(br, r)
              },
            )
          })
        }
      },
    )
}
