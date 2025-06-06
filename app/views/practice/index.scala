package views.html
package practice

import controllers.routes

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._

object index {

  def apply(data: lila.practice.UserPractice)(implicit ctx: Context) =
    views.html.base.layout(
      title = trans.practice.txt(),
      moreCss = cssTag("analyse.practice"),
      moreJs = embedJsUnsafe(s"""$$('.do-reset').on('click', function() {
if (confirm('You will lose your practice progress!')) this.parentNode.submit();
});"""),
      openGraph = lila.app.ui
        .OpenGraph(
          title = trans.practice.txt(),
          description = "Learn how to master the most common shogi positions",
          url = s"$netBaseUrl${routes.Practice.index.url}",
        )
        .some,
      robots = false,
      canonicalPath = lila.common.CanonicalPath(routes.Practice.index).some,
    ) {
      main(cls := "page-menu")(
        st.aside(cls := "page-menu__menu practice-side")(
          i(cls := "fat"),
          h1(trans.practice()),
          h2("makes your shogi perfect"),
          div(cls := "progress")(
            div(cls := "text")("Progress: ", data.progressPercent, "%"),
            div(cls := "bar", style := s"width: ${data.progressPercent}%"),
          ),
          postForm(action := routes.Practice.reset)(
            if (ctx.isAuth)
              (data.nbDoneChapters > 0) option a(cls := "do-reset")("Reset my progress")
            else a(href := routes.Auth.signup)("Sign up to save your progress"),
          ),
        ),
        div(cls := "page-menu__content practice-app")(
          div(cls := "temporary-practice")(
            a(href := "https://github.com/WandererXII/lishogi/issues/359")(
              "Under construction - if you want to help click here.",
            ),
          ),
          data.structure.sections.map { section =>
            st.section(
              h2(section.name),
              div(cls := "studies")(
                section.studies.map { stud =>
                  val prog = data.progressOn(stud.id)
                  a(
                    cls  := s"study ${if (prog.complete) "done" else "ongoing"}",
                    href := routes.Practice.show(section.id, stud.slug, stud.id.value),
                  )(
                    ctx.isAuth option span(cls := "ribbon-wrapper")(
                      span(cls := "ribbon")(prog.done, " / ", prog.total),
                    ),
                    i(cls := s"${stud.id}"),
                    span(cls := "text")(
                      h3(stud.name),
                      em(stud.desc),
                    ),
                  )
                },
              ),
            )
          },
        ),
      )
    }
}
