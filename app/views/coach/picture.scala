package views.html
package coach

import lishogi.api.Context
import lishogi.app.templating.Environment._
import lishogi.app.ui.ScalatagsTemplate._

import controllers.routes

object picture {

  def apply(c: lishogi.coach.Coach.WithUser, error: Option[String] = None)(implicit ctx: Context) =
    views.html.account.layout(
      title = s"${c.user.titleUsername} coach picture",
      evenMoreJs = jsTag("coach.form.js"),
      evenMoreCss = cssTag("coach.editor"),
      active = "coach"
    ) {
      div(cls := "account coach-edit coach-picture box")(
        div(cls := "top")(
          div(cls := "picture_wrap")(
            widget.pic(c, 250)
          ),
          h1(widget.titleName(c))
        ),
        div(cls := "forms")(
          error.map { e =>
            p(cls := "error")(e)
          },
          postForm(action := routes.Coach.pictureApply(), enctype := "multipart/form-data", cls := "upload")(
            p("Max size: ", lishogi.db.Photographer.uploadMaxMb, "MB."),
            form3.file.image("picture"),
            form3.actions(
              a(href := routes.Coach.edit())(trans.cancel()),
              form3.submit("Upload profile picture")
            )
          ),
          c.coach.hasPicture option
            st.form(action := routes.Coach.pictureDelete(), cls := "delete")(
              submitButton(cls := "confirm button button-empty button-red")("Delete profile picture")
            )
        )
      )
    }
}
