package views.html.oAuth.app

import play.api.data.Form

import lishogi.api.Context
import lishogi.app.templating.Environment._
import lishogi.app.ui.ScalatagsTemplate._

import controllers.routes

object form {

  def create(form: Form[_])(implicit ctx: Context) = {
    val title = "New OAuth App"
    views.html.account.layout(title = title, active = "oauth.app") {
      div(cls := "account oauth box box-pad")(
        h1(title),
        postForm(cls := "form3", action := routes.OAuthApp.create())(
          div(cls := "form-group")(
            "Want to build something that integrates with and extends Lishogi? Register a new OAuth App to get started developing on the Lishogi API."
          ),
          inner(form)
        )
      )
    }
  }

  def edit(app: lishogi.oauth.OAuthApp, form: Form[_])(implicit ctx: Context) = {
    val title = s"Edit ${app.name}"
    views.html.account.layout(title = title, active = "oauth.app") {
      div(cls := "account oauth box box-pad")(
        h1(title),
        table(cls := "codes")(
          tbody(
            tr(th("Client ID"), td(app.clientId.value)),
            tr(th("Client Secret"), td(app.clientSecret.value))
          )
        ),
        br,
        br,
        standardFlash(),
        postForm(cls := "form3", action := routes.OAuthApp.update(app.clientId.value))(
          div(cls := "form-group")(
            "Here's a ",
            a(href := "https://github.com/lichess-org/api/tree/master/example/oauth-authorization-code")(
              "Lichess OAuth app example"
            ),
            ", and ",
            a(href := "https://lichess.org/api")(
              "Lichess' API documentation"
            ),
            " to get an idea on how Lishogi's API looks."
          ),
          inner(form)
        )
      )
    }
  }

  private def inner(form: Form[_])(implicit ctx: Context) =
    frag(
      errMsg(form),
      form3.group(form("name"), raw("App name"))(form3.input(_)),
      form3.group(form("description"), raw("App description"))(form3.textarea(_)()),
      form3.split(
        form3.group(form("homepageUri"), raw("Homepage URL"), half = true)(form3.input(_, typ = "url")),
        form3.group(
          form("redirectUri"),
          raw("Callback URL"),
          half = true,
          help = frag("It must match the URL in your code").some
        )(form3.input(_, typ = "url"))
      ),
      form3.actions(
        a(href := routes.OAuthApp.index())("Cancel"),
        form3.submit(trans.apply())
      )
    )
}
