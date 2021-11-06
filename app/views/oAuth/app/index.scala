package views.html.oAuth.app

import lishogi.api.Context
import lishogi.app.templating.Environment._
import lishogi.app.ui.ScalatagsTemplate._

import controllers.routes

object index {

  def apply(made: List[lishogi.oauth.OAuthApp], used: List[lishogi.oauth.AccessToken.WithApp])(implicit
      ctx: Context
  ) =
    views.html.account.layout(title = "OAuth Apps", active = "oauth.app")(
      div(cls := "account oauth")(
        used.nonEmpty option div(cls := "oauth-used box")(
          h1(id := "used")("OAuth Apps"),
          standardFlash(cls := "box__pad"),
          table(cls := "slist slist-pad")(
            used.map { t =>
              tr(
                td(
                  strong(t.app.name),
                  " by ",
                  userIdLink(t.app.author.some),
                  br,
                  em(t.token.scopes.map(_.name).mkString(", "))
                ),
                td(cls := "date")(
                  a(href := t.app.homepageUri)(t.app.homepageUri),
                  br,
                  t.token.usedAt map { at =>
                    frag("Last used ", momentFromNow(at))
                  }
                ),
                td(cls := "action")(
                  postForm(action := routes.OAuthApp.revoke(t.token.id.value))(
                    submitButton(
                      cls := "button button-empty button-red confirm text",
                      title := s"Revoke access from ${t.app.name}",
                      dataIcon := "q"
                    )("Revoke")
                  )
                )
              )
            }
          )
        ),
        div(cls := "oauth-made box")(
          h1(id := "made")("My OAuth Apps"),
          p(cls := "box__pad")(
            "Want to build something that integrates with and extends Lishogi? ",
            a(href := routes.OAuthApp.create())("Register a new OAuth App"),
            " to get started developing with the Lishogi API.",
            br,
            br,
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
          table(cls := "slist slist-pad")(
            made.map { t =>
              tr(
                td(
                  strong(t.name),
                  br,
                  t.description.map { em(_) }
                ),
                td(cls := "date")(
                  a(href := t.homepageUri)(t.homepageUri),
                  br,
                  "Created ",
                  momentFromNow(t.createdAt)
                ),
                td(cls := "action")(
                  a(
                    href := routes.OAuthApp.edit(t.clientId.value),
                    cls := "button button-empty",
                    title := "Edit this app",
                    dataIcon := "m"
                  ),
                  postForm(action := routes.OAuthApp.delete(t.clientId.value))(
                    submitButton(
                      cls := "button button-empty button-red confirm",
                      title := "Delete this app",
                      dataIcon := "q"
                    )
                  )
                )
              )
            }
          )
        )
      )
    )
}
