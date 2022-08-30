package views.html
package game

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.game.{ Game, Player, Pov }

object widgets {

  private val separator = " • "

  def apply(
      games: Seq[Game],
      user: Option[lila.user.User] = None,
      ownerLink: Boolean = false
  )(implicit ctx: Context): Frag =
    games map { g =>
      val fromPlayer  = user flatMap g.player
      val firstPlayer = fromPlayer | g.firstPlayer
      st.article(cls := "game-row paginated")(
        a(cls        := "game-row__overlay", href := gameLink(g, firstPlayer.color, ownerLink)),
        div(cls := "game-row__board")(
          gameSfen(Pov(g, firstPlayer), withLink = false, withTitle = false)
        ),
        div(cls := "game-row__infos")(
          div(cls := "header", dataIcon := bits.gameIcon(g))(
            div(cls := "header__text")(
              strong(
                if (g.imported)
                  frag(
                    span("IMPORT"),
                    g.notationImport.flatMap(_.user).map { user =>
                      frag(" ", trans.by(userIdLink(user.some, None, false)))
                    },
                    separator,
                    bits.variantLink(g.variant)
                  )
                else
                  frag(
                    showClock(g),
                    separator,
                    g.perfType.fold("SFEN")(_.trans),
                    separator,
                    if (g.rated) trans.rated.txt() else trans.casual.txt()
                  )
              ),
              g.notationImport.flatMap(_.date).fold(momentFromNow(g.createdAt))(frag(_)),
              g.tournamentId.map { tourId =>
                frag(separator, tournamentLink(tourId))
              } orElse
                g.simulId.map { simulId =>
                  frag(separator, views.html.simul.bits.link(simulId))
                } orElse
                g.swissId.map { swissId =>
                  frag(separator, views.html.swiss.bits.link(lila.swiss.Swiss.Id(swissId)))
                }
            )
          ),
          div(cls := "versus")(
            gamePlayer(g.sentePlayer),
            div(cls := "swords", dataIcon := "U"),
            gamePlayer(g.gotePlayer)
          ),
          div(cls := "result")(
            if (g.isBeingPlayed) trans.playingRightNow()
            else {
              if (g.finishedOrAborted)
                span(cls := g.winner.flatMap(w => fromPlayer.map(p => if (p == w) "win" else "loss")))(
                  gameEndStatus(g),
                  g.winner.map { winner =>
                    frag(
                      ", ",
                      winner.color.fold(trans.blackIsVictorious(), trans.whiteIsVictorious())
                    )
                  }
                )
              else g.turnColor.fold(trans.blackPlays(), trans.whitePlays())
            }
          ),
          frag(br, br),
          g.metadata.analysed option
            div(cls := "metadata text", dataIcon := "")(trans.computerAnalysisAvailable()),
          g.notationImport.flatMap(_.user).map { user =>
            div(cls := "metadata")(
              s"${if (g.isKifImport) "KIF" else "CSA"} import by ",
              userIdLink(user.some)
            )
          }
        )
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
            else trans.nbDays.pluralSame(days)
          )
        }
        .getOrElse {
          span(title := trans.unlimited.txt())("∞")
        }
    }

  private lazy val anonSpan = span(cls := "anon")(lila.user.User.anonymous)

  private def gamePlayer(player: Player)(implicit ctx: Context) =
    div(cls := s"player ${player.color.name}")(
      player.playerUser map { playerUser =>
        frag(
          userIdLink(playerUser.id.some, withOnline = false),
          br,
          player.berserk option berserkIconSpan,
          playerUser.rating,
          player.provisional option "?",
          playerUser.ratingDiff map { d =>
            frag(" ", showRatingDiff(d))
          }
        )
      } getOrElse {
        player.aiLevel map { level =>
          frag(
            span(aiName(level, false)),
            br,
            aiRating(level)
          )
        } getOrElse {
          (player.nameSplit.fold[Frag](anonSpan) { case (name, rating) =>
            frag(
              span(name),
              rating.map { r =>
                frag(br, r)
              }
            )
          })
        }
      }
    )
}
