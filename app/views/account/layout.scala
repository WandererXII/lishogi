package views.html.account

import controllers.routes

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._

object layout {

  def apply(
      title: String,
      active: String,
      evenMoreCss: Frag = emptyFrag,
      evenMoreJs: Frag = emptyFrag,
  )(body: Frag)(implicit ctx: Context): Frag =
    views.html.base.layout(
      title = title,
      moreCss = frag(cssTag("user.account"), evenMoreCss),
      moreJs = frag(jsTag("user.account"), evenMoreJs),
    ) {
      def activeCls(c: String) = cls := active.activeO(c)
      main(cls := "account page-menu")(
        st.nav(cls := "page-menu__menu subnav")(
          lila.pref.PrefCateg.all.map { categ =>
            a(activeCls(categ.slug), href := routes.Pref.form(categ.slug))(
              bits.categName(categ),
            )
          },
          a(activeCls("kid"), href := routes.Account.kid)(
            trans.kidMode(),
          ),
          div(cls := "sep"),
          a(activeCls("editProfile"), href := routes.Account.profile)(
            trans.editProfile(),
          ),
          isGranted(_.Coach) option a(activeCls("coach"), href := routes.Coach.edit)(
            trans.coach.lishogiCoach(),
          ),
          div(cls := "sep"),
          a(activeCls("password"), href := routes.Account.passwd)(
            trans.changePassword(),
          ),
          a(activeCls("email"), href := routes.Account.email)(
            trans.changeEmail(),
          ),
          a(activeCls("username"), href := routes.Account.username)(
            trans.changeUsername(),
          ),
          a(activeCls("twofactor"), href := routes.Account.twoFactor)(
            trans.tfa.twoFactorAuth(),
          ),
          a(activeCls("security"), href := routes.Account.security)(
            trans.security(),
          ),
          div(cls := "sep"),
          a(href := routes.Plan.index)(trans.patron.lishogiPatron()),
          div(cls := "sep"),
          a(activeCls("oauth.token"), href := routes.OAuthToken.index)(
            "API Access tokens",
          ),
          div(cls := "sep"),
          a(activeCls("close"), href := routes.Account.close)(
            trans.settings.closeAccount(),
          ),
        ),
        div(cls := "page-menu__content")(body),
      )
    }
}
