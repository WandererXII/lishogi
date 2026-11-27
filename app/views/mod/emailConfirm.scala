package views.html.mod

import controllers.routes

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._

object emailConfirm {

  def apply(query: String, user: Option[lila.user.User], email: Option[lila.common.EmailAddress])(
      implicit ctx: Context,
  ) =
    views.html.base.layout(
      title = "Email confirmation",
      moreCss = cssTag("user.mod.misc"),
      moreJs = embedJsUnsafe("""$('.mod-confirm form.search input').on('paste', function() {
setTimeout(function() { $(this).parent().submit(); }.bind(this), 50);
}).each(function() {
this.setSelectionRange(this.value.length, this.value.length);
});"""),
    ) {
      main(cls := "page-menu")(
        views.html.mod.menu("email"),
        div(cls := "mod-confirm page-menu__content box box-pad")(
          h1("Confirm a user email"),
          p(
            "If you provide an email, it will find the corresponding account, if any, and let you confirm it.",
            br,
            "If you provide an email and a username, it will let you set the email to that user, ",
            "but only if the user has not yet confirmed their email.",
          ),
          st.form(cls := "search", action := routes.Mod.emailConfirm, method := "GET")(
            input(
              name        := "q",
              placeholder := "<email> <username (optional)>",
              value       := user.fold(query)(_ => ""),
              autofocus,
            ),
          ),
          user.map { u =>
            table(cls := "slist")(
              thead(
                tr(
                  th("User"),
                  th("Email"),
                  th("Games"),
                  th("Marks"),
                  th("Created"),
                  th("Active"),
                  th("Confirmed"),
                ),
              ),
              tbody(
                tr(
                  td(showUsername(u, withModLink = true), s" (${u.perfs.bestRating})"),
                  td(email.fold("-")(_.value)),
                  td(u.count.game.localize),
                  td(
                    u.marks.engine option "ENGINE",
                    u.marks.boost option "BOOSTER",
                    u.marks.troll option "SHADOWBAN",
                    u.disabled option "CLOSED",
                  ),
                  td(momentFromNow(u.createdAt)),
                  td(u.seenAt.map(momentFromNow(_))),
                  td(style := "font-size:2em")(
                    if (!u.everLoggedIn) iconTag(Icons.correct)(cls := "is-green")
                    else iconTag(Icons.cancel)(cls                  := "is-red"),
                  ),
                ),
              ),
            )
          },
          user.filterNot(_.everLoggedIn).map { _ =>
            postForm(action := routes.Mod.emailConfirmApply)(
              form3.hidden("q", query),
              submitButton(cls := "button")("Set email and confirm"),
            )
          },
        ),
      )
    }
}
