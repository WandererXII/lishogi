package views.html.streamer

import controllers.routes
import lishogi.api.Context
import lishogi.app.templating.Environment._
import lishogi.app.ui.ScalatagsTemplate._
import lishogi.common.paginator.Paginator

object index {

  import trans.streamer._

  private val dataDedup = attr("data-dedup")

  def apply(
      live: List[lishogi.streamer.Streamer.WithUserAndStream],
      pager: Paginator[lishogi.streamer.Streamer.WithUser],
      requests: Boolean
  )(implicit ctx: Context) = {

    val title = if (requests) "Streamer approval requests" else lishogiStreamers.txt()

    def widget(s: lishogi.streamer.Streamer.WithUser, stream: Option[lishogi.streamer.Stream]) =
      frag(
        if (requests) a(href := s"${routes.Streamer.edit}?u=${s.user.username}", cls := "overlay")
        else
          bits.redirectLink(s.user.username, stream.isDefined.some)(cls := "overlay"),
        stream.isDefined option span(cls := "ribbon")(span(trans.streamer.live())),
        bits.pic(s.streamer, s.user),
        div(cls := "overview")(
          h1(dataIcon := "")(titleTag(s.user.title), stringValueFrag(s.streamer.name)),
          s.streamer.headline.map(_.value).map { d =>
            p(cls := s"headline ${if (d.size < 60) "small" else if (d.size < 120) "medium" else "large"}")(d)
          },
          div(cls := "services")(
            s.streamer.twitch.map { twitch =>
              div(cls := "service twitch")(twitch.minUrl)
            },
            s.streamer.youTube.map { youTube =>
              div(cls := "service youTube")(youTube.minUrl)
            }
          ),
          div(cls := "ats")(
            stream.map { s =>
              p(cls := "at")(
                currentlyStreaming(strong(s.status))
              )
            } getOrElse frag(
              p(cls := "at")(trans.lastSeenActive(momentFromNow(s.streamer.seenAt))),
              s.streamer.liveAt.map { liveAt =>
                p(cls := "at")(lastStream(momentFromNow(liveAt)))
              }
            )
          )
        )
      )

    views.html.base.layout(
      title = title,
      moreCss = cssTag("streamer.list"),
      moreJs = infiniteScrollTag
    ) {
      main(cls := "page-menu")(
        bits.menu(if (requests) "requests" else "index", none)(ctx)(cls := " page-menu__menu"),
        div(cls := "page-menu__content box streamer-list")(
          h1(dataIcon := "", cls := "text")(title),
          !requests option div(cls := "list live")(
            live.map { s =>
              st.article(cls := "streamer")(widget(s.withoutStream, s.stream))
            }
          ),
          div(cls := "list infinitescroll")(
            (live.size % 2 == 1) option div(cls := "none"),
            pager.currentPageResults.map { s =>
              st.article(cls := "streamer paginated", dataDedup := s.streamer.id.value)(widget(s, none))
            },
            pagerNext(
              pager,
              np =>
                addQueryParameter(
                  addQueryParameter(routes.Streamer.index().url, "page", np),
                  "requests",
                  if (requests) 1 else 0
                )
            ).map {
              frag(_, div(cls := "none")) // don't break the even/odd CSS flow
            }
          )
        )
      )
    }
  }
}
