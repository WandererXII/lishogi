package views.html
package round

import play.api.libs.json.{ JsObject, Json }

import lishogi.api.Context
import lishogi.app.templating.Environment._
import lishogi.app.ui.ScalatagsTemplate._
import lishogi.common.String.html.safeJsonValue
import lishogi.game.Pov

object watcher {

  def apply(
      pov: Pov,
      data: JsObject,
      tour: Option[lishogi.tournament.TourAndTeamVs],
      simul: Option[lishogi.simul.Simul],
      cross: Option[lishogi.game.Crosstable.WithMatchup],
      userTv: Option[lishogi.user.User] = None,
      chatOption: Option[lishogi.chat.UserChat.Mine],
      bookmarked: Boolean
  )(implicit ctx: Context) = {

    val chatJson = chatOption map { c =>
      chat.json(
        c.chat,
        name = trans.spectatorRoom.txt(),
        timeout = c.timeout,
        withNoteAge = ctx.isAuth option pov.game.secondsSinceCreation,
        public = true,
        resourceId = lishogi.chat.Chat.ResourceId(s"game/${c.chat.id}"),
        palantir = ctx.me.exists(_.canPalantir)
      )
    }

    bits.layout(
      variant = pov.game.variant,
      title = gameVsText(pov.game, withRatings = true),
      moreJs = frag(
        roundNvuiTag,
        roundTag,
        embedJsUnsafe(s"""lishogi=window.lishogi||{};customWS=true;onload=function(){
LishogiRound.boot(${safeJsonValue(
          Json.obj(
            "data" -> data,
            "i18n" -> jsI18n(pov.game),
            "chat" -> chatJson
          )
        )})}""")
      ),
      openGraph = povOpenGraph(pov).some,
      shogiground = false
    )(
      main(cls := "round")(
        st.aside(cls := "round__side")(
          bits.side(pov, data, tour, simul, userTv, bookmarked),
          chatOption.map(_ => chat.frag)
        ),
        bits.roundAppPreload(pov, false),
        div(cls := "round__underboard")(bits.crosstable(cross, pov.game)),
        div(cls := "round__underchat")(bits underchat pov.game)
      )
    )
  }

  def crawler(pov: Pov, initialFen: Option[shogi.format.FEN], kif: shogi.format.Notation)(implicit
      ctx: Context
  ) =
    bits.layout(
      variant = pov.game.variant,
      title = gameVsText(pov.game, withRatings = true),
      openGraph = povOpenGraph(pov).some,
      shogiground = false
    )(
      frag(
        main(cls := "round")(
          st.aside(cls := "round__side")(
            game.side(pov, initialFen, none, simul = none, userTv = none, bookmarked = false),
            div(cls := "for-crawler")(
              h1(titleGame(pov.game)),
              p(describePov(pov)),
              div(cls := "kif")(kif.render)
            )
          ),
          div(cls := "round__board main-board")(shogiground(pov))
        )
      )
    )
}
