package views.html.streamer

import controllers.routes
import play.api.i18n.Lang

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.user.User

object bits extends Context.ToLang {

  import trans.streamer._

  def pic(s: lila.streamer.Streamer.WithUserAndStream)(implicit lang: Lang) =
    s.streamer.picturePath match {
      case Some(path) =>
        img(
          cls             := "picture",
          attr("loading") := "lazy",
          src := urlOrImageStorageUrl(
            path.value,
            lila.common.ImageStorage.Imgproxy.opts(width = 600, height = 600).some,
          ),
          alt := s"${s.user.titleUsername} - ${trans.streamer.lishogiStreamer.txt()}",
        )
      case _ =>
        img(
          cls := "default picture",
          src := staticUrl("images/placeholder.png"),
          alt := "Default Lishogi streamer picture",
        )
    }

  def overview(s: lila.streamer.Streamer.WithUserAndStream)(implicit lang: Lang): Frag =
    div(cls := "overview")(
      h2(s.streamer.name),
      bits.headline(s.streamer),
      bits.ats(s),
      bits.services(s.streamer),
    )

  def headline(s: lila.streamer.Streamer): Frag = {
    val d = s.headline.fold("-")(_.value)
    p(
      cls := s"headline ${if (d.sizeIs < 60) "small"
        else if (d.sizeIs < 120) "medium"
        else "large"}",
    )(d)
  }

  def ats(s: lila.streamer.Streamer.WithUserAndStream)(implicit lang: Lang): Frag =
    div(cls := "ats")(
      p(showUsername(s.user)),
      s.stream.map { s =>
        p(cls := "at")(currentlyStreaming(strong(s.status)))
      } getOrElse frag(
        div(cls := "at-wrap")(
          p(cls := "at")(trans.lastSeenActive(momentFromNow(s.streamer.seenAt))),
          s.streamer.liveAt.map { liveAt =>
            p(cls := "at")(lastStream(momentFromNow(liveAt)))
          },
        ),
      ),
    )

  def menu(active: String, s: Option[lila.streamer.Streamer.WithUser])(implicit ctx: Context) =
    st.nav(cls := "subnav")(
      a(cls := active.active("index"), href := routes.Streamer.index())(allStreamers()),
      s.map { st =>
        frag(
          a(cls := active.active("show"), href := routes.Streamer.show(st.streamer.id.value))(
            st.streamer.name,
          ),
          (ctx.is(st.user) || isGranted(_.Streamers)) option
            a(
              cls  := active.active("edit"),
              href := s"${routes.Streamer.edit}?u=${st.streamer.id.value}",
            )(
              editPage(),
            ),
        )
      } getOrElse a(cls := active.active("create"), href := routes.Streamer.edit)(yourPage()),
      isGranted(_.Streamers) option a(
        cls  := active.active("requests"),
        href := s"${routes.Streamer.index()}?requests=1",
      )("Approval requests"),
    )

  def services(streamer: lila.streamer.Streamer): Frag =
    div(cls := "services")(
      streamer.youTube map { youTube =>
        a(
          targetBlank,
          href := youTube.fullUrl,
        )(
          img(cls := "s-yt", src := staticUrl(s"images/brands/yt.png")),
        )
      },
      streamer.twitch map { twitch =>
        a(
          targetBlank,
          href := twitch.fullUrl,
        )(
          img(cls := "s-twitch", src := staticUrl(s"images/brands/twitch.svg")),
        )
      },
    )

  def redirectLink(username: String, isStreaming: Option[Boolean] = None) =
    isStreaming match {
      case Some(false) => a(href := routes.Streamer.show(username))
      case _ =>
        a(
          href := routes.Streamer.redirect(username),
          targetBlank,
          rel := "nofollow",
        )
    }

  def liveStreams(l: lila.streamer.LiveStreams): Frag =
    l.streams.map { s =>
      redirectLink(s.streamer.id.value)(
        cls   := "stream highlight",
        title := s.status,
      )(
        strong(cls := "text", dataIcon := Icons.mic)(s.streamer.name),
        " ",
        s.status,
      )
    }

  private def contextual(userId: User.ID)(implicit lang: Lang): Frag =
    redirectLink(userId)(cls := "context-streamer text", dataIcon := Icons.mic)(
      xIsStreaming(usernameOrId(userId)),
    )

  def contextualWrap(userIds: Seq[User.ID], hidden: Boolean = false)(implicit lang: Lang): Frag =
    userIds.nonEmpty option div(cls := s"context-streamers${hidden ?? " none"}")(
      userIds map views.html.streamer.bits.contextual,
    )

  def contextualWrap(userId: User.ID)(implicit lang: Lang): Frag =
    div(cls := "context-streamers")(
      views.html.streamer.bits.contextual(userId),
    )

  def rules(implicit lang: Lang) =
    ul(cls := "streamer-rules")(
      h2(trans.streamer.rules()),
      ul(
        li(rule1()),
        li(rule2()),
        li(rule3()),
      ),
      h2(perks()),
      ul(
        li(perk1()),
        li(perk2()),
        li(perk3()),
        li(perk4()),
      ),
    )
}
