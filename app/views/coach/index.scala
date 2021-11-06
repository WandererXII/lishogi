package views.html
package coach

import play.api.i18n.Lang

import lishogi.api.Context
import lishogi.app.templating.Environment._
import lishogi.app.ui.ScalatagsTemplate._
import lishogi.i18n.LangList
import lishogi.common.paginator.Paginator

import controllers.routes

object index {

  import trans.coach._

  def apply(
      pager: Paginator[lishogi.coach.Coach.WithUser],
      lang: Option[Lang],
      order: lishogi.coach.CoachPager.Order,
      langCodes: Set[String]
  )(implicit
      ctx: Context
  ) =
    views.html.base.layout(
      title = lishogiCoaches.txt(),
      moreCss = cssTag("coach"),
      moreJs = infiniteScrollTag
    ) {
      val langSelections = ("all", "All languages") :: lishogi.i18n.I18nLangPicker
        .sortFor(LangList.popularNoRegion.filter(l => langCodes(l.code)), ctx.req)
        .map { l =>
          l.code -> LangList.name(l)
        }
      main(cls := "coach-list coach-full-page")(
        st.aside(cls := "coach-list__side coach-side")(
          p(
            becomeACoach(),
            br,
            sendApplication(contactEmailLink)
          )
        ),
        div(cls := "coach-list__main coach-main box")(
          div(cls := "box__top")(
            h1(lishogiCoaches()),
            div(cls := "box__top__actions")(
              views.html.base.bits.mselect(
                "coach-lang",
                lang.fold("All languages")(LangList.name),
                langSelections
                  .map { case (code, name) =>
                    a(
                      href := routes.Coach.search(code, order.key),
                      cls := (code == lang.fold("all")(_.code)).option("current")
                    )(name)
                  }
              ),
              views.html.base.bits.mselect(
                "coach-sort",
                order.name,
                lishogi.coach.CoachPager.Order.all map { o =>
                  a(
                    href := routes.Coach.search(lang.fold("all")(_.code), o.key),
                    cls := (order == o).option("current")
                  )(
                    o.name
                  )
                }
              )
            )
          ),
          div(cls := "list infinitescroll")(
            pager.currentPageResults.map { c =>
              st.article(cls := "coach-widget paginated", attr("data-dedup") := c.coach.id.value)(
                widget(c, link = true)
              )
            },
            pagerNext(
              pager,
              np =>
                addQueryParameter(routes.Coach.search(lang.fold("all")(_.code), order.key).url, "page", np)
            ).map {
              frag(_, div(cls := "none")) // don't break the even/odd CSS flow
            }
          )
        )
      )
    }
}
