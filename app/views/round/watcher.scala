package views.html
package round

import play.api.libs.json.JsObject
import play.api.libs.json.Json

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.game.Pov
import lila.socket.Socket.SocketVersion

object watcher {

  def apply(
      pov: Pov,
      data: JsObject,
      socketVersion: SocketVersion,
      tour: Option[lila.tournament.TourAndTeamVs],
      simul: Option[lila.simul.Simul],
      cross: Option[lila.game.Crosstable.WithMatchup],
      userTv: Option[lila.user.User] = None,
      chatOption: Option[lila.chat.Chat.Game],
      bookmarked: Boolean,
  )(implicit ctx: Context) = {

    val chatJson = chatOption map { c =>
      chat.gameJson(
        gameChat = c,
        game = pov.game,
      )
    }

    bits.layout(
      variant = pov.game.variant,
      title = gameVsText(pov.game, withRanks = true),
      moreJs = frag(
        ctx.blind option roundNvuiTag,
        moduleJsTag(
          "round",
          Json.obj(
            "data"          -> data,
            "socketVersion" -> socketVersion.value,
            "chat"          -> chatJson,
          ),
        ),
      ),
      openGraph = povOpenGraph(pov).some,
      shogiground = false,
    )(
      main(cls := s"round ${mainVariantClass(pov.game.variant)}")(
        st.aside(cls := "round__side")(
          bits.side(pov, tour, simul, userTv, analysis = false, bookmarked),
          chatOption.map(_ => chat.frag),
        ),
        bits.roundAppPreload(pov, false),
        div(cls := "round__underboard")(bits.crosstable(cross, pov.game)),
        div(cls := "round__underchat")(views.html.chat.membersGame),
      ),
    )
  }

  def crawler(pov: Pov, kif: shogi.format.Notation)(implicit
      ctx: Context,
  ) =
    bits.layout(
      variant = pov.game.variant,
      title = gameVsText(pov.game, withRanks = true),
      openGraph = povOpenGraph(pov).some,
      shogiground = false,
    )(
      frag(
        main(cls := s"round ${mainVariantClass(pov.game.variant)}")(
          st.aside(cls := "round__side")(
            game
              .side(pov, none, simul = none, userTv = none, analysis = false, bookmarked = false),
            div(cls := "for-crawler")(
              h1(titleGame(pov.game)),
              p(describePov(pov)),
              div(cls := "kif")(kif.render),
            ),
          ),
          div(cls := s"round__board main-board ${variantClass(pov.game.variant)}")(shogiground(pov)),
        ),
      ),
    )
}
