package views.html
package account

import lishogi.api.Context
import lishogi.app.templating.Environment._
import lishogi.app.ui.ScalatagsTemplate._

import controllers.routes

object security {

  def apply(u: lishogi.user.User, sessions: List[lishogi.security.LocatedSession], curSessionId: String)(implicit
      ctx: Context
  ) =
    account.layout(title = s"${u.username} - ${trans.security.txt()}", active = "security") {
      div(cls := "account security box")(
        h1(trans.security()),
        standardFlash(cls := "box__pad"),
        div(cls := "box__pad")(
          p(trans.thisIsAListOfDevicesThatHaveLoggedIntoYourAccount()),
          sessions.length > 1 option div(
            trans.alternativelyYouCanX {
              postForm(cls := "revoke-all", action := routes.Account.signout("all"))(
                submitButton(cls := "button button-empty button-red confirm")(
                  trans.revokeAllSessions()
                )
              )
            }
          )
        ),
        table(cls := "slist slist-pad")(
          sessions.map { s =>
            tr(
              td(cls := "icon")(
                span(
                  cls := s"is-${if (s.session.id == curSessionId) "gold" else "green"}",
                  dataIcon := (if (s.session.isMobile) "" else "")
                )
              ),
              td(cls := "info")(
                span(cls := "ip")(s.session.ip),
                " ",
                span(cls := "location")(s.location.map(_.toString)),
                p(cls := "ua")(s.session.ua),
                s.session.date.map { date =>
                  p(cls := "date")(
                    momentFromNow(date),
                    s.session.id == curSessionId option span(cls := "current")("[CURRENT]")
                  )
                }
              ),
              td(
                s.session.id != curSessionId option
                  postForm(action := routes.Account.signout(s.session.id))(
                    submitButton(cls := "button button-red", title := trans.logOut.txt(), dataIcon := "L")
                  )
              )
            )
          }
        )
      )
    }
}
