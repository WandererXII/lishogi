package views.html.clas

import controllers.routes

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.clas.Clas
import lila.clas.ClasInvite

object invite {

  def show(
      c: Clas,
      invite: ClasInvite,
  )(implicit ctx: Context) =
    views.html.base.layout(
      moreCss = cssTag("misc.clas"),
      title = c.name,
    ) {
      main(cls := "page-small box box-pad page clas-invitation")(
        h1(c.name),
        p(c.desc),
        br,
        br,
        trans.clas.invitedToXByY(
          c.name,
          showUsernameById(invite.created.by.some, withOnline = false),
        ),
        br,
        br,
        invite.accepted.map {
          case true  => p(cls := "success")(trans.success.txt())
          case false => p(cls := "warning")(trans.decline.txt())
        },
        invite.accepted.fold(true)(false.==) option
          postForm(cls := "form3", action := routes.Clas.invitationAccept(invite._id.value))(
            form3.actions(
              if (!invite.accepted.has(false))
                form3.submit(
                  trans.decline(),
                  nameValue = ("v" -> false.toString).some,
                  klass = "button-red button-fat",
                  icon = Icons.cancel.some,
                )
              else p,
              form3.submit(
                trans.accept(),
                klass = "button-green button-fat",
                nameValue = ("v" -> true.toString).some,
              ),
            ),
          ),
      )
    }
}
