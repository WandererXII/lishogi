package views.html.blog

import controllers.routes

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.blog.FullPost
import lila.blog.MiniPost
import lila.common.String.html.richText

object bits {

  private[blog] def menu(year: Option[Int], hasActive: Boolean = true)(implicit ctx: Context) =
    st.nav(cls := "page-menu__menu subnav")(
      a(
        cls  := (year.isEmpty && hasActive).option("active"),
        href := langHrefJP(routes.Blog.index()),
      )(
        trans.blog(),
      ),
      lila.blog.allYears map { y =>
        a(cls := (year has y).option("active"), href := langHrefJP(routes.Blog.year(y)))(y)
      },
    )

  private[blog] def postCard(
      post: MiniPost,
      postClass: Option[String] = None,
      header: Tag = h2,
  )(implicit ctx: Context) =
    a(cls := postClass)(href := routes.Blog.show(post.id))(
      st.img(src := post.image),
      div(cls := "content")(
        header(cls := "title")(post.title),
        span(post.shortlede),
        semanticDate(post.date),
      ),
    )

  private[blog] def metas(
      post: FullPost,
  )(implicit ctx: Context, prismic: lila.blog.BlogApi.Context) =
    div(cls := "meta-headline")(
      div(cls := "meta")(
        span(cls := "text", dataIcon := Icons.clock)(semanticDate(post.date)),
        span(cls := "text", dataIcon := Icons.person)(richText(post.author)),
        span(cls := "text", dataIcon := Icons.starFull)(post.category),
      ),
      strong(cls := "headline")(
        post.doc.getHtml(s"${post.coll}.shortlede", prismic.linkResolver).map(raw),
      ),
    )

}
