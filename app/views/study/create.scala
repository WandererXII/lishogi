package views.html.study

import controllers.routes

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.study.Study

object create {

  private def studyButton(s: Study.IdName) =
    submitButton(name := "as", value := s.id.value, cls := "submit button")(s.name.value)

  def apply(
      data: lila.study.StudyForm.importGame.Data,
      owner: List[Study.IdName],
      contrib: List[Study.IdName],
  )(implicit ctx: Context) =
    views.html.site.message(
      title = trans.toStudy.txt(),
      icon = Icons.study.some,
      back = data.sfen.map(sf => routes.Editor.parseArg(s"${data.variant.key}/${sf.value}").url),
      moreCss = cssTag("analyse.study.create").some,
    ) {
      div(cls := "study-create")(
        postForm(action := routes.Study.create)(
          input(tpe := "hidden", name := "gameId", value      := data.gameId),
          input(tpe := "hidden", name := "orientation", value := data.orientationStr),
          input(tpe := "hidden", name := "sfen", value        := data.sfen.map(_.value)),
          input(tpe := "hidden", name := "notation", value    := data.notationStr),
          input(tpe := "hidden", name := "variant", value     := data.variantStr),
          h2(trans.study.whereDoYouWantToStudyThat()),
          p(
            submitButton(
              name     := "as",
              value    := "study",
              cls      := "submit button large new text",
              dataIcon := Icons.study,
            )(trans.study.createStudy()),
          ),
          div(cls := "studies")(
            div(
              h2(trans.study.myStudies()),
              owner map studyButton,
            ),
            div(
              h2(trans.study.studiesIContributeTo()),
              contrib map studyButton,
            ),
          ),
        ),
      )
    }
}
