package views.html
package round

import play.api.libs.json.Json

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.game.Pov
import lila.socket.Socket.SocketVersion

object player {

  def apply(
      pov: Pov,
      data: play.api.libs.json.JsObject,
      socketVersion: SocketVersion,
      tour: Option[lila.tournament.GameView],
      simul: Option[lila.simul.Simul],
      cross: Option[lila.game.Crosstable.WithMatchup],
      playing: List[Pov],
      chatOption: Option[lila.chat.Chat.Game],
      bookmarked: Boolean,
  )(implicit ctx: Context) = {

    val chatJson = chatOption.map { c =>
      chat.gameJson(
        gameChat = c,
        game = pov.game,
        loginRequired = false,
      )
    }

    bits.layout(
      variant = pov.game.variant,
      title = s"${trans.play.txt()} ${if (ctx.pref.isZen) "ZEN" else playerText(pov.opponent)}",
      moreJs = frag(
        ctx.blind option roundNvuiTag,
        moduleJsTag(
          "round",
          Json
            .obj(
              "data"          -> data,
              "socketVersion" -> socketVersion.value,
              "userId"        -> ctx.userId,
              "chat"          -> chatJson,
            ),
        ),
      ),
      moreCss = pov.game.isProMode ?? frag(
        cssTag("round.pro-mode"),
        proPieceSetOverride(pov.game.variant),
      ),
      openGraph = povOpenGraph(pov).some,
      shogiground = false,
      playing = true,
    )(
      main(cls := s"round ${mainVariantClass(pov.game.variant)}")(
        st.aside(cls := "round__side")(
          bits.side(
            pov,
            tour.map(_.tourAndTeamVs),
            simul,
            analysis = false,
            bookmarked = bookmarked,
          ),
          chatOption.map(_ => chat.frag),
        ),
        bits.roundAppPreload(pov, true),
        div(cls := "round__underboard")(
          bits.crosstable(cross, pov.game),
          (playing.nonEmpty || simul.exists(_ isHost ctx.me)) option
            div(
              cls := List(
                "round__now-playing" -> true,
              ),
            )(bits.others(playing, simul)),
        ),
        div(cls := "round__underchat")(views.html.chat.membersGame),
      ),
    )
  }

  private def proPieceSetOverride(
      variant: shogi.variant.Variant,
  )(implicit ctx: Context): Option[Frag] =
    if (variant.dobutsu)
      !ctx.currentDobutsuPieceSet.pro ?? dobutsuPieceSprite(lila.pref.DobutsuPieceSet.default).some
    else if (variant.kyotoshogi)
      !ctx.currentKyoPieceSet.pro ?? kyoPieceSprite(lila.pref.KyoPieceSet.default).some
    else if (variant.chushogi)
      !ctx.currentChuPieceSet.pro ?? chuPieceSprite(lila.pref.ChuPieceSet.default).some
    else !ctx.currentPieceSet.pro ?? defaultPieceSprite(lila.pref.PieceSet.default).some
}
