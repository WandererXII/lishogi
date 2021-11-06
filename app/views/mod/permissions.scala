package views.html.mod

import lishogi.api.Context
import lishogi.app.templating.Environment._
import lishogi.app.ui.ScalatagsTemplate._
import lishogi.user.User
import lishogi.security.Permission

import controllers.routes

object permissions {

  def apply(u: User, me: User)(implicit ctx: Context) =
    views.html.base.layout(
      title = s"${u.username} permissions",
      moreCss = frag(
        cssTag("mod.permission"),
        cssTag("form3")
      )
    ) {
      val userPerms = Permission(u.roles)
      main(cls := "mod-permissions page-small box box-pad")(
        h1(userLink(u), " permissions"),
        standardFlash(),
        postForm(cls := "form3", action := routes.Mod.permissions(u.username))(
          p(cls := "granted")("In green, permissions enabled manually or by a package."),
          div(cls := "permission-list")(
            lishogi.security.Permission.categorized
              .filter { case (_, ps) => ps.exists(canGrant(me, _)) }
              .map { case (categ, perms) =>
                st.section(
                  h2(categ),
                  perms
                    .filter(canGrant(me, _))
                    .map { perm =>
                      val id = s"permission-${perm.dbKey}"
                      div(
                        cls := isGranted(perm, u) option "granted",
                        title := isGranted(perm, u).?? {
                          Permission.findGranterPackage(userPerms, perm).map { p =>
                            s"Granted by package: $p"
                          }
                        }
                      )(
                        span(
                          form3.cmnToggle(
                            id,
                            "permissions[]",
                            checked = u.roles.contains(perm.dbKey),
                            value = perm.dbKey
                          )
                        ),
                        label(`for` := id)(perm.name)
                      )
                    }
                )
              }
          ),
          form3.actions(
            a(href := routes.User.show(u.username))(trans.cancel()),
            submitButton(cls := "button")(trans.save())
          )
        )
      )
    }
}
