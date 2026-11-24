package views.html
package auth

import controllers.routes
import play.api.data.Form

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.security.DataForm.SignupData

object signup {

  def apply(form: Form[_], formTime: String)(implicit ctx: Context) =
    views.html.base.layout(
      title = trans.signUp.txt(),
      moreCss = cssTag("user.auth"),
      moreJs = frag(
        jsTag("user.signup"),
        fingerprintTag,
      ),
      withHrefLangs = lila.i18n.LangList.All.some,
    ) {
      main(cls := "auth auth-signup box box-pad")(
        h1(trans.signUp()),
        postForm(id := "signup_form", cls := "form3", action := routes.Auth.signupPost)(
          auth.bits
            .formFields(form("username"), form("password"), form("email").some, register = true),
          input(id := "signup-fp-input", name := "fp", tpe := "hidden"),
          form3.hidden(SignupData.formTimerField, formTime),
          honeypot,
          div(cls := "form-group text", dataIcon := Icons.infoCircle)(
            trans.computersAreNotAllowedToPlay(),
            br,
            small(
              trans.byRegisteringYouAgreeToBeBoundByOur(
                a(href := routes.Article.tos, targetBlank)(trans.termsOfService()),
              ),
            ),
          ),
          agreement(form("agreement"), form.errors.exists(_.key startsWith "agreement.")),
          form3.globalError(form),
          form3.submit(trans.signUp(), icon = none, klass = "big"),
        ),
      )
    }

  private def agreement(form: play.api.data.Field, error: Boolean)(implicit ctx: Context) =
    div(cls := "agreement")(
      error option p(
        strong(cls := "error")(
          "You must agree to the Lishogi policies listed below:",
        ),
      ),
      agreements.map { case (field, i18n) =>
        form3.checkbox(form(field), i18n(), required = true)
      },
    )

  // must stay empty, and must not be filled by browsers and password managers
  private val honeypot =
    div(cls := "auth-signup__site", attr("aria-hidden") := "true")(
      label(`for` := "signup-site")("Leave this field empty"),
      input(
        id                     := "signup-site",
        name                   := SignupData.honeypotField,
        tpe                    := "text",
        tabindex               := -1,
        autocomplete           := "off",
        attr("data-1p-ignore") := "true",
        attr("data-bwignore")  := "true",
        attr("data-lpignore")  := "true",
      ),
    )

  private val agreements = List(
    "assistance" -> trans.agreementAssistance,
    "nice"       -> trans.agreementNice,
    "account"    -> trans.agreementAccount,
    "policy"     -> trans.agreementPolicy,
  )
}
