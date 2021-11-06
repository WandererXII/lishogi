package views.html.mod

import play.api.i18n.Lang

import lishogi.app.templating.Environment._
import lishogi.app.ui.ScalatagsTemplate._

import controllers.routes

object impersonate {

  def apply(user: lishogi.user.User)(implicit lang: Lang) =
    div(id := "impersonate")(
      div(cls := "meat")(
        "You are impersonating ",
        userLink(user, withOnline = false)
      ),
      div(cls := "actions")(
        postForm(action := routes.Mod.impersonate("-"))(
          submitButton(cls := "button button-empty")("Quit")
        )
      )
    )
}
