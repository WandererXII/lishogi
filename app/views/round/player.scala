package views.html
package round

import play.api.libs.json.Json

import lishogi.api.Context
import lishogi.app.templating.Environment._
import lishogi.app.ui.ScalatagsTemplate._
import lishogi.common.String.html.safeJsonValue
import lishogi.game.Pov

object player {

  def apply(
      pov: Pov,
      data: play.api.libs.json.JsObject,
      tour: Option[lishogi.tournament.GameView],
      simul: Option[lishogi.simul.Simul],
      cross: Option[lishogi.game.Crosstable.WithMatchup],
      playing: List[Pov],
      chatOption: Option[lishogi.chat.Chat.GameOrEvent],
      bookmarked: Boolean
  )(implicit ctx: Context) = {

    val chatJson = chatOption.map(_.either).map {
      case Left(c) =>
        chat.restrictedJson(
          c,
          name = trans.chatRoom.txt(),
          timeout = false,
          withNoteAge = ctx.isAuth option pov.game.secondsSinceCreation,
          public = false,
          resourceId = lishogi.chat.Chat.ResourceId(s"game/${c.chat.id}"),
          palantir = ctx.me.exists(_.canPalantir)
        )
      case Right((c, res)) =>
        chat.json(
          c.chat,
          name = trans.chatRoom.txt(),
          timeout = c.timeout,
          public = true,
          resourceId = res
        )
    }

    bits.layout(
      variant = pov.game.variant,
      title = s"${trans.play.txt()} ${if (ctx.pref.isZen) "ZEN" else playerText(pov.opponent)}",
      moreJs = frag(
        roundNvuiTag,
        roundTag,
        embedJsUnsafe(s"""lishogi=window.lishogi||{};customWS=true;onload=function(){
LishogiRound.boot(${safeJsonValue(
          Json
            .obj(
              "data"   -> data,
              "i18n"   -> jsI18n(pov.game),
              "userId" -> ctx.userId,
              "chat"   -> chatJson
            )
        )})}""")
      ),
      openGraph = povOpenGraph(pov).some,
      shogiground = false,
      playing = true
    )(
      main(cls := "round")(
        st.aside(cls := "round__side")(
          bits.side(pov, data, tour.map(_.tourAndTeamVs), simul, bookmarked = bookmarked),
          chatOption.map(_ => chat.frag)
        ),
        bits.roundAppPreload(pov, true),
        div(cls := "round__underboard")(
          bits.crosstable(cross, pov.game),
          (playing.nonEmpty || simul.exists(_ isHost ctx.me)) option
            div(
              cls := List(
                "round__now-playing" -> true,
                "blindfold"          -> ctx.pref.isBlindfold
              )
            )(bits.others(playing, simul))
        ),
        div(cls := "round__underchat")(bits underchat pov.game)
      )
    )
  }
}
