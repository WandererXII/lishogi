package views.html
package tournament

import play.api.libs.json.Json

import lishogi.api.Context
import lishogi.app.templating.Environment._
import lishogi.app.ui.ScalatagsTemplate._
import lishogi.common.String.html.safeJsonValue
import lishogi.tournament.Tournament
import lishogi.user.User

import controllers.routes

object show {

  def apply(
      tour: Tournament,
      verdicts: lishogi.tournament.Condition.All.WithVerdicts,
      data: play.api.libs.json.JsObject,
      chatOption: Option[lishogi.chat.UserChat.Mine],
      streamers: List[User.ID],
      shieldOwner: Option[lishogi.tournament.TournamentShield.OwnerId]
  )(implicit ctx: Context) =
    views.html.base.layout(
      title = s"${tour.name()} #${tour.id}",
      moreJs = frag(
        jsModule("tournament"),
        embedJsUnsafe(s"""lishogi=lishogi||{};lishogi.tournament=${safeJsonValue(
          Json.obj(
            "data"   -> data,
            "i18n"   -> bits.jsI18n,
            "userId" -> ctx.userId,
            "chat" -> chatOption.map { c =>
              chat.json(
                c.chat,
                name = trans.chatRoom.txt(),
                timeout = c.timeout,
                public = true,
                resourceId = lishogi.chat.Chat.ResourceId(s"tournament/${c.chat.id}")
              )
            }
          )
        )}""")
      ),
      moreCss = cssTag {
        if (tour.isTeamBattle) "tournament.show.team-battle"
        else "tournament.show"
      },
      shogiground = false,
      openGraph = lishogi.app.ui
        .OpenGraph(
          title = s"${tour.name()}: ${tour.variant.name} ${tour.clock.show} ${tour.mode.name} #${tour.id}",
          url = s"$netBaseUrl${routes.Tournament.show(tour.id).url}",
          description =
            s"${tour.nbPlayers} players compete in the ${showEnglishDate(tour.startsAt)} ${tour.name()}. " +
              s"${tour.clock.show} ${tour.mode.name} games are played during ${tour.minutes} minutes. " +
              tour.winnerId.fold("Winner is not yet decided.") { winnerId =>
                s"${usernameOrId(winnerId)} takes the prize home!"
              }
        )
        .some
    )(
      main(cls := s"tour${tour.schedule
        .?? { sched =>
          s" tour-sched tour-sched-${sched.freq.name} tour-speed-${sched.speed.name} tour-variant-${sched.variant.key} tour-id-${tour.id}"
        }}")(
        st.aside(cls := "tour__side")(
          tournament.side(tour, verdicts, streamers, shieldOwner, chatOption.isDefined)
        ),
        div(cls := "tour__main")(div(cls := "box")),
        tour.isCreated option div(cls := "tour__faq")(
          faq(tour.mode.rated.some, tour.isPrivate.option(tour.id))
        )
      )
    )
}
