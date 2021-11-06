package views.html.tv

import lishogi.api.Context
import lishogi.app.templating.Environment._
import lishogi.app.ui.ScalatagsTemplate._

object games {

  def apply(channel: lishogi.tv.Tv.Channel, povs: List[lishogi.game.Pov], champions: lishogi.tv.Tv.Champions)(implicit
      ctx: Context
  ) =
    views.html.base.layout(
      title = s"${channel.name} • ${trans.currentGames.txt()}",
      moreCss = cssTag("tv.games")
    ) {
      main(cls := "page-menu tv-games")(
        st.aside(cls := "page-menu__menu")(
          side.channels(channel, champions, "/games")
        ),
        div(cls := "page-menu__content now-playing")(
          povs map views.html.game.bits.mini
        )
      )
    }
}
