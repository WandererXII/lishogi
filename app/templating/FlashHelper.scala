package lila.app
package templating

import lila.api.Context
import lila.app.ui.ScalatagsTemplate._

trait FlashHelper { self: I18nHelper =>

  def standardFlash(implicit ctx: Context): Option[Frag] =
    successFlash orElse failureFlash

  private def successFlash(implicit ctx: Context): Option[Frag] =
    ctx.flash("success").map { msg =>
      flashMessage(cls := "flash-success")(
        if (msg.isEmpty) trans.success() else msg,
      )
    }

  private def failureFlash(implicit ctx: Context): Option[Frag] =
    ctx.flash("failure").map { msg =>
      flashMessage(cls := "flash-failure")(
        if (msg.isEmpty) "Failure" else msg,
      )
    }

  private def flashMessage(modifiers: Modifier*)(contentModifiers: Modifier*): Frag =
    div(modifiers)(cls := "flash")(
      div(cls := "flash__content")(contentModifiers),
    )
}
