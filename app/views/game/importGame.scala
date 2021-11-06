package views.html
package game

import lishogi.api.Context
import lishogi.app.templating.Environment._
import lishogi.app.ui.ScalatagsTemplate._

import controllers.routes

object importGame {

  private def analyseHelp(implicit ctx: Context) =
    ctx.isAnon option a(cls := "blue", href := routes.Auth.signup())(trans.youNeedAnAccountToDoThat())

  def apply(form: play.api.data.Form[_])(implicit ctx: Context) =
    views.html.base.layout(
      title = trans.importGame.txt(),
      moreCss = cssTag("importer"),
      moreJs = jsTag("importer.js"),
      openGraph = lishogi.app.ui
        .OpenGraph(
          title = "Paste KIF or CSA shogi game",
          url = s"$netBaseUrl${routes.Importer.importGame().url}",
          description = trans.importGameKifCsaExplanation.txt()
        )
        .some
    ) {
      main(cls := "importer page-small box box-pad")(
        h1(trans.importGame()),
        p(cls := "explanation")(trans.importGameKifCsaExplanation()),
        postForm(cls := "form3 import", action := routes.Importer.sendGame())(
          div(cls := "import-top")(
            div(cls := "left")(
              form3.group(form("notation"), trans.pasteTheKifCsaStringHere())(form3.textarea(_)())
            ),
            div(cls := "right")(
              form3.group(form("notationFile"), raw("Or upload a KIF/CSA file"), klass = "upload") { f =>
                form3.file.notation(f.name)
              },
              form3.checkbox(
                form("analyse"),
                trans.requestAComputerAnalysis(),
                help = Some(analyseHelp),
                disabled = ctx.isAnon
              ),
              form3.action(form3.submit(trans.importGame(), "/".some))
            )
          ),
          form("notation").value flatMap { notation =>
            lishogi.importer
              .ImportData(notation, none)
              .preprocess(none)
              .fold(
                err =>
                  frag(
                    pre(cls := "error")(err.toList mkString "\n"),
                    br,
                    br
                  ).some,
                _ => none
              )
          }
        )
      )
    }
}
