package views.html
package account

import controllers.routes
import play.api.data.Form

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._

object reopen {

  def form(form: Form[_], captcha: lila.common.Captcha, error: Option[String] = None)(implicit
      ctx: Context,
  ) =
    views.html.base.layout(
      title = "Reopen your account",
      moreCss = cssTag("user.auth"),
      moreJs = captchaTag,
    ) {
      main(cls := "page-small box box-pad")(
        h1("Reopen your account"),
        p(
          "If you closed your account, but have since changed your mind, you get one chance of getting your account back.",
        ),
        p(strong("This will only work once.")),
        p("If you close your account a second time, there will be no way of recovering it."),
        hr,
        p(
          "Solve the shogi captcha below, and we will send you an email containing a link to reopen your account.",
        ),
        postForm(cls := "form3", action := routes.Account.reopenApply)(
          error.map { err =>
            p(cls := "error")(strong(err))
          },
          form3.group(form("username"), trans.username())(form3.input(_)(autofocus)),
          form3
            .group(
              form("email"),
              trans.email(),
              help = frag("Email address associated to the account").some,
            )(
              form3.input(_, typ = "email"),
            ),
          views.html.base.captcha(form, captcha),
          form3.action(form3.submit(trans.emailMeALink())),
        ),
      )
    }

  def sent(implicit ctx: Context) =
    views.html.base.layout(
      title = "Reopen your account",
    ) {
      main(cls := "page-small box box-pad")(
        h1(cls := "is-green text", dataIcon := "E")(trans.checkYourEmail()),
        p("We've sent you an email with a link."),
        p(trans.ifYouDoNotSeeTheEmailCheckOtherPlaces()),
      )
    }
}
