package views
package html.tournament

import controllers.routes

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.tournament.Tournament
import lila.tournament.TournamentShield

object side {

  private val separator = " - "

  def apply(
      tour: Tournament,
      verdicts: lila.tournament.Condition.All.WithVerdicts,
      streamers: List[lila.user.User.ID],
      shieldOwner: Option[TournamentShield.OwnerId],
      chat: Boolean,
  )(implicit ctx: Context) =
    frag(
      div(cls := "tour__meta")(
        st.section(
          tournamentIconTag(tour),
          div(
            p(
              tour.timeControl.show,
              separator,
              views.html.game.bits.variantLink(tour.variant, tour.perfType.some),
              tour.position.isDefined ?? s"$separator${trans.thematic.txt()}",
            ),
            tour.mode.fold(trans.casualTournament, trans.ratedTournament)(),
            separator,
            tour.format.trans,
            (isGranted(_.ManageTournament) || (ctx.userId
              .has(tour.createdBy) && !tour.isFinished)) option frag(
              " ",
              a(href := routes.Tournament.edit(tour.id), title := trans.edit.txt())(
                iconTag(Icons.gear),
              ),
            ),
          ),
        ),
        ctx.userId.has(tour.createdBy) option div(cls := "tour-creator-buttons")(
          button(cls := "button button-thin manage-players")(
            trans.managePlayers(),
          ),
          tour.teamBattle.isDefined option a(
            cls  := "button button-thin tour-team-edit",
            href := routes.Tournament.teamBattleEdit(tour.id),
          )(
            "Edit team battle",
          ),
        ),
        tour.teamBattle map { battle =>
          st.section(cls := "team-battle")(
            p(cls := "team-battle__title text", dataIcon := Icons.banner)(
              s"Battle of ${battle.teams.size} teams and ${battle.nbLeaders} leaders",
            ),
          )
        },
        tour.spotlight map { s =>
          st.section(
            lila.common.String.html.markdownLinks(s.description),
            shieldOwner map { owner =>
              p(cls := "defender")(
                s"${trans.arena.defender.txt()}:",
                showUsernameById(owner.value.some),
              )
            },
          )
        },
        tour.looksLikePrize option bits.userPrizeDisclaimer,
        verdicts.relevant option st.section(
          cls := List(
            "conditions" -> true,
            "accepted"   -> (ctx.isAuth && verdicts.accepted),
            "refused"    -> (ctx.isAuth && !verdicts.accepted),
          ),
        )(
          div(
            (verdicts.list.sizeIs < 2) option p(trans.conditionOfEntry()),
            verdicts.list map { v =>
              p(
                cls := List(
                  "condition text" -> true,
                  "accepted"       -> v.verdict.accepted,
                  "refused"        -> !v.verdict.accepted,
                ),
              )(v.condition match {
                case lila.tournament.Condition.TeamMember(teamId, teamName) =>
                  trans.mustBeInTeam(teamLink(teamId, teamName, withIcon = false))
                case c => c.name(tour.perfType)
              })
            },
          ),
        ),
        tour.isArena && tour.noBerserk option div(cls := "text", dataIcon := Icons.berserk)(
          trans.arena.noBerserkAllowed(),
        ),
        tour.isArena && tour.noStreak option div(cls := "text", dataIcon := Icons.streak)(
          trans.arena.noArenaStreaks(),
        ),
        tour.proMode option div(cls := "text", dataIcon := Icons.crown)(
          trans.proMode(),
        ),
        !tour.isScheduled option frag(trans.by(showUsernameById(tour.createdBy.some)), br),
        frag(
          absClientDateTime(
            tour.startsAt,
          ),
          " - ",
          absClientDateTime(tour.finishesAt),
        ),
        tour.position.map { sfen =>
          p(
            trans.fromPosition.txt(),
            separator,
            views.html.base.bits.sfenAnalysisLink(tour.variant, sfen),
          )
        },
        (tour.hasArrangements && ctx.pref.tourChallenge != lila.pref.Pref.Challenge.ALWAYS) option a(
          cls      := "text challenge-warning",
          href     := routes.Pref.form("privacy"),
          dataIcon := Icons.warning,
        )(
          "You cannot receive challenges from all users",
        ),
      ),
      views.html.streamer.bits.contextualWrap(streamers),
      chat option views.html.chat.frag,
    )

}
