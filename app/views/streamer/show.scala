package views.html.streamer

import controllers.routes

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.common.String.html.richText

object show {

  import trans.streamer._

  def apply(
      s: lila.streamer.Streamer.WithUserAndStream,
      activities: Vector[lila.activity.ActivityView],
  )(implicit ctx: Context) =
    views.html.base.layout(
      title = xStreamsShogi.txt(s.streamer.name),
      moreCss = cssTag("misc.streamer.show"),
      openGraph = lila.app.ui
        .OpenGraph(
          title = xStreamsShogi.txt(s.streamer.name),
          description = shorten(
            ~(s.streamer.headline.map(_.value) orElse s.streamer.description.map(_.value)),
            152,
          ),
          url = s"$netBaseUrl${routes.Streamer.show(s.user.username)}",
          image = s.streamer.picturePath.map(p =>
            urlOrImageStorageUrl(
              p.value,
              lila.common.ImageStorage.Imgproxy
                .opts(
                  width = 600,
                  height = 600,
                )
                .some,
            ),
          ),
        )
        .some,
    )(
      main(cls := "page-menu streamer-show")(
        st.aside(cls := "page-menu__menu")(
          bits.menu("show", s.withoutStream.some),
        ),
        div(cls := "page-menu__content")(
          div(cls := "box streamer")(
            (s.stream.isDefined) option div(cls := "streamer-live-box")(
              bits.redirectLink(s.user.id)(cls := "text", dataIcon := Icons.mic)(
                xIsStreaming(s.streamer.name),
              ),
            ),
            views.html.streamer.header(s),
            s.streamer.description map { desc =>
              div(cls := "description")(richText(desc.value))
            },
            a(cls := "ratings", href := routes.User.show(s.user.username))(
              s.user.best6Perfs.map { showPerfRating(s.user, _) },
            ),
            views.html.activity(s.user, activities),
          ),
        ),
      ),
    )
}
