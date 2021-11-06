package views.html.forum

import lishogi.api.Context
import lishogi.app.templating.Environment._
import lishogi.app.ui.ScalatagsTemplate._
import lishogi.common.paginator.Paginator
import lishogi.common.String.html.nl2br

import controllers.routes

object search {

  def apply(text: String, pager: Paginator[lishogi.forum.PostView])(implicit ctx: Context) = {
    val title = s"""${trans.search.search.txt()} "${text.trim}""""
    views.html.base.layout(
      title = title,
      moreJs = infiniteScrollTag,
      moreCss = cssTag("forum")
    )(
      main(cls := "box box search")(
        div(cls := "box__top")(
          h1(
            a(href := routes.ForumCateg.index(), dataIcon := "I", cls := "text"),
            title
          ),
          bits.searchForm(text)
        ),
        strong(cls := "nb-results box__pad")(pager.nbResults, " posts found"),
        table(cls := "slist slist-pad search__results")(
          if (pager.nbResults > 0)
            tbody(cls := "infinitescroll")(
              pagerNextTable(pager, n => routes.ForumPost.search(text, n).url) | tr,
              pager.currentPageResults.map { view =>
                tr(cls := "paginated")(
                  td(
                    a(cls := "post", href := routes.ForumPost.redirect(view.post.id))(
                      view.categ.name,
                      " - ",
                      view.topic.name,
                      "#",
                      view.post.number
                    ),
                    p(nl2br(shorten(view.post.text.replace("\n\n", "\n"), 200)))
                  ),
                  td(cls := "info")(
                    momentFromNow(view.post.createdAt),
                    br,
                    authorLink(view.post)
                  )
                )
              }
            )
          else tbody(tr(td("No forum post found")))
        )
      )
    )
  }
}
