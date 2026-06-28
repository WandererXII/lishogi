package views.html
package article

import controllers.routes
import play.api.mvc.Call

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.article.Article
import lila.common.paginator.Paginator
import lila.user.User

object list {

  def recent(
      pag: Paginator[Article.Preview],
      lang: Option[Article.Lang.Code],
      category: Option[Article.Category],
  )(implicit ctx: Context) =
    layout(
      title = trans.article.recentArticles.txt(),
      active = "recent",
      langSearch = lang,
      pag = pag,
      url = lang.fold(routes.Article.default())(l => routes.Article.recent(l)),
      canonicalPath = lila.common.CanonicalPath(routes.Article.default(1)).some,
      categoryKey = category.map(_.key) orElse "all".some,
    )

  def best(
      pag: Paginator[Article.Preview],
      lang: Option[Article.Lang.Code],
      category: Option[Article.Category],
  )(implicit ctx: Context) =
    layout(
      title = trans.article.bestArticles.txt(),
      active = "best",
      langSearch = lang,
      pag = pag,
      url = lang.fold(routes.Article.bestDefault())(l => routes.Article.best(l)),
      canonicalPath = lila.common.CanonicalPath(routes.Article.bestDefault(1)).some,
      categoryKey = category.map(_.key) orElse "all".some,
    )

  def mineLikes(
      pag: Paginator[Article.Preview],
  )(implicit ctx: Context) =
    layout(
      title = trans.article.likedArticles.txt(),
      active = "mineLikes",
      langSearch = none,
      pag = pag,
      url = routes.Article.mineLikes(),
    )

  def byAuthor(pag: Paginator[Article.Preview], author: User, me: Option[User])(implicit
      ctx: Context,
  ) =
    layout(
      title = trans.article.articlesWrittenByX.txt(author.titleUsername),
      active = if (me.exists(_.is(author))) "mine" else "author",
      langSearch = none,
      pag = pag,
      url = routes.Article.author(author.id),
      canonicalPath = lila.common.CanonicalPath(routes.Article.author(author.id)).some,
    )

  def submitted(
      pag: Paginator[Article.Preview],
  )(implicit ctx: Context) =
    layout(
      title = trans.article.submittedArticles.txt(),
      active = "submitted",
      langSearch = none,
      pag = pag,
      url = routes.Article.submitted(),
    )

  def system(
      pag: Paginator[Article.Preview],
  )(implicit ctx: Context) =
    layout(
      title = "System",
      active = "system",
      langSearch = none,
      pag = pag,
      url = routes.Article.system(),
    )

  private[article] def paginate(
      pager: Paginator[Article.Preview],
      url: Call,
      langSearch: Option[Article.Lang.Code],
  )(implicit
      ctx: Context,
  ) =
    if (pager.currentPageResults.isEmpty)
      div(cls := "noarticles")(
        iconTag(Icons.article),
        p(trans.article.noneYet()),
      )
    else
      div(cls := "articles list infinitescroll")(
        pager.currentPageResults.map { a =>
          div(cls := "article-widget paginated")(bits.widget(a, langSearch))
        },
        pagerNext(pager, np => addQueryParameter(url.url, "page", np)),
      )

  private def layout(
      title: String,
      active: String,
      langSearch: Option[Article.Lang.Code],
      pag: Paginator[Article.Preview],
      url: Call,
      canonicalPath: Option[lila.common.CanonicalPath] = none,
      categoryKey: Option[String] = none,
  )(implicit ctx: Context) =
    views.html.base.layout(
      title = title,
      moreCss = cssTag("misc.article.index"),
      wrapClass = "full-screen-force",
      moreJs = infiniteScrollTag,
      canonicalPath = canonicalPath,
    ) {
      main(cls := "page-menu")(
        bits.menu(active),
        main(cls := "page-menu__content article-index box")(
          div(cls := "box__top")(
            h1(title),
            active == "recent" option bits.langSelect(langSearch.getOrElse(Article.Lang.allCode)),
            ctx.isAuth option a(
              href     := routes.Article.form,
              cls      := "button button-green text",
              dataIcon := Icons.createNew,
            )(trans.article.newArticle()),
          ),
          categoryKey map { cat =>
            div(
              cls := "chip-list",
            )(
              a(cls := s"chip${("all" == cat) ?? " selected"}", href := url)(
                trans.article.categoryAll(),
              ) ::
                (Article.Category.all map { c =>
                  a(
                    cls  := s"chip${(c.key == cat) ?? " selected"}",
                    href := s"${url.url}?category=${c.key}",
                  )(
                    Article.Category.trans(c),
                  )
                }),
            )
          },
          paginate(pag, url, langSearch),
        ),
      )
    }
}
