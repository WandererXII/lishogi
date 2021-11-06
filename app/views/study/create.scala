package views.html.study

import lishogi.api.Context
import lishogi.app.templating.Environment._
import lishogi.app.ui.ScalatagsTemplate._
import lishogi.study.Study

import controllers.routes

object create {

  private def studyButton(s: Study.IdName) =
    submitButton(name := "as", value := s.id.value, cls := "submit button")(s.name.value)

  def apply(
      data: lishogi.study.StudyForm.importGame.Data,
      owner: List[Study.IdName],
      contrib: List[Study.IdName]
  )(implicit ctx: Context) =
    views.html.site.message(
      title = trans.toStudy.txt(),
      icon = Some("4"),
      back = data.fenStr.map(routes.Editor.load(_).url),
      moreCss = cssTag("study.create").some
    ) {
      div(cls := "study-create")(
        postForm(action := routes.Study.create())(
          input(tpe := "hidden", name := "gameId", value := data.gameId),
          input(tpe := "hidden", name := "orientation", value := data.orientationStr),
          input(tpe := "hidden", name := "fen", value := data.fenStr),
          input(tpe := "hidden", name := "notation", value := data.notationStr),
          input(tpe := "hidden", name := "variant", value := data.variantStr),
          h2(trans.study.whereDoYouWantToStudyThat()),
          p(
            submitButton(
              name := "as",
              value := "study",
              cls := "submit button large new text",
              dataIcon := "4"
            )(trans.study.createStudy())
          ),
          div(cls := "studies")(
            div(
              h2(trans.study.myStudies()),
              owner map studyButton
            ),
            div(
              h2(trans.study.studiesIContributeTo()),
              contrib map studyButton
            )
          )
        )
      )
    }
}
