package views.html.blog

import controllers.routes

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._

object show {

  def apply(post: lila.blog.FullPost)(implicit ctx: Context, prismic: lila.blog.BlogApi.Context) =
    views.html.base.layout(
      title = post.title,
      moreCss = cssTag("misc.blog"),
      moreJs = jsTag("misc.expand-text"),
      openGraph = lila.app.ui
        .OpenGraph(
          `type` = "article",
          image = post.image.some,
          title = post.title,
          url = s"$netBaseUrl${routes.Blog.show(post.id).url}",
          description = ~post.doc.getText(s"${post.coll}.shortlede"),
        )
        .some,
      csp = defaultCsp.withTwitter.some,
      withHrefLangs = post.allLangIds.map { ids =>
        lila.i18n.LangList
          .Custom(
            Map(
              "en" -> routes.Blog.show(ids.en).url,
              "ja" -> routes.Blog.show(ids.ja).url,
            ),
          )
      },
    )(
      main(cls := "page-menu page-small")(
        bits.menu(none, false),
        div(cls := s"blog page-menu__content box post")(
          h1(post.title),
          bits.metas(post),
          div(cls := "illustration")(st.img(src := post.image)),
          div(cls := "body expand-text")(
            post.doc
              .getHtml(s"${post.coll}.body", prismic.linkResolver)
              .map(lila.blog.Youtube.fixStartTimes)
              .map(lila.blog.BlogTransform.removeProtocol)
              .map(lila.blog.BlogTransform.markdown.apply)
              .map(raw),
          ),
          ctx.noKid option
            div(cls := "footer")(
              (
                post.date isAfter org.joda.time.DateTime.now.minusWeeks(4)
              ) option
                a(
                  href     := routes.Blog.discuss(post.doc.id),
                  cls      := "button text discuss",
                  dataIcon := "d",
                )(
                  trans.discussBlogForum(),
                ),
              views.html.base.bits.connectLinks,
            ),
        ),
      ),
    )
}
