package views.html
package user

import controllers.routes

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.common.String.html.richText
import lila.user.User

object bots {

  def apply(users: List[User])(implicit ctx: Context) = {

    val title = s"${users.size} ${trans.onlineBots.txt()}"

    views.html.base.layout(
      title = title,
      moreCss = frag(cssTag("misc.slist"), cssTag("user.list")),
      wrapClass = "full-screen-force",
    )(
      main(cls := "page-menu bots")(
        user.bits.communityMenu("bots"),
        div(cls := "bots page-menu__content box")(
          div(cls := "box__top")(h1(title)),
          table(cls := "slist slist-pad")(
            tbody(
              users.sortBy(-_.playTime.??(_.total)) map { u =>
                tr(
                  td(userLink(u)),
                  u.profile
                    .ifTrue(ctx.noKid)
                    .ifTrue(!u.marks.troll || ctx.is(u))
                    .flatMap(_.nonEmptyBio)
                    .map { bio =>
                      td(richText(shorten(bio, 400), nl2br = false))
                    } | td,
                  td(cls := "rating")(u.best3Perfs.map {
                    showPerfRating(u, _)
                  }),
                  u.playTime.fold(td) { playTime =>
                    td(
                      p(
                        cls      := "text",
                        dataIcon := Icons.toriGate,
                        st.title := trans.tpTimeSpentPlaying.txt(showPeriod(playTime.totalPeriod)),
                      )(showPeriod(playTime.totalPeriod)),
                      playTime.nonEmptyTvPeriod.map { tvPeriod =>
                        p(
                          cls      := "text",
                          dataIcon := Icons.television,
                          st.title := trans.tpTimeSpentOnTV.txt(showPeriod(tvPeriod)),
                        )(showPeriod(tvPeriod))
                      },
                    )
                  },
                  if (ctx is u) td
                  else {
                    td(
                      a(
                        dataIcon := Icons.challenge,
                        cls      := List("button button-empty text" -> true),
                        st.title := trans.challengeToPlay.txt(),
                        href     := s"${routes.Lobby.home}?user=${u.username}#friend",
                      )(trans.play()),
                    )
                  },
                )
              },
            ),
          ),
        ),
      ),
    )
  }

}
