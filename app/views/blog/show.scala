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
      withHrefLangs = {
        val langMap =
          post.doc.alternateLanguages.foldLeft(
            Map[String, String](post.lang.language -> routes.Blog.show(post.id).url),
          ) { case (acc, cur) =>
            acc.updated(
              lila.blog.BlogLang.fromLangCode(cur.lang).language,
              routes.Blog.show(cur.id).url,
            )
          }
        (langMap.size > 1) option lila.i18n.LangList.Custom(langMap)
      },
    )(
      main(cls := "page-menu page-small")(
        bits.menu(none, false),
        div(cls := s"blog page-menu__content box post")(
          h1(post.title),
          div(cls := "illustration")(st.img(src := post.image)),
          bits.metas(post),
          div(cls := "body expand-text")(
            post.doc
              .getHtml(s"${post.coll}.body", prismic.linkResolver)
              .map(lila.blog.Youtube.fixStartTimes)
              .map(lila.blog.BlogTransform.removeProtocol)
              .map(raw),
          ),
          post.doc.uid.ifTrue(ctx.noKid) map { uid =>
            div(cls := "footer")(
              a(
                href     := routes.Blog.discuss(uid, post.doc.id),
                cls      := "button text discuss",
                dataIcon := Icons.talkAlt,
              )(
                trans.discussBlogForum(),
              ),
              views.html.base.bits.connectLinks,
            )
          },
        ),
      ),
    )
}
