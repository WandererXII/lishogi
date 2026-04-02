package views.html.base

import controllers.routes
import play.api.i18n.Lang

import shogi.format.forsyth.Sfen
import shogi.variant.Variant

import lila.app.ui.ScalatagsTemplate._

object bits {

  def mselect(id: String, current: Frag, items: List[Frag]) =
    div(cls := "mselect")(
      input(
        tpe   := "checkbox",
        cls   := "mselect__toggle fullscreen-toggle",
        st.id := s"mselect-$id",
      ),
      label(`for` := s"mselect-$id", cls := "mselect__label")(current),
      label(`for` := s"mselect-$id", cls := "fullscreen-mask"),
      st.nav(cls := "mselect__list")(items),
    )

  lazy val stage = a(
    href  := "https://lishogi.org",
    style := """
background: #7f1010;
color: #fff;
position: fixed;
bottom: 0;
left: 0;
padding: .5em 1em;
border-top-right-radius: 3px;
z-index: 99;
""",
  )(
    "This is an empty Lishogi preview website, go to lishogi.org instead",
  )

  val connectLinks =
    div(cls := "connect-links")(
      a(href := "https://discord.gg/YFtpMGg3rR", target := "_blank", rel := "nofollow")("Discord"),
      a(href := "https://github.com/WandererXII/lishogi", target := "_blank", rel := "nofollow")(
        "GitHub",
      ),
      a(href := "https://twitter.com/lishogi", target := "_blank", rel := "nofollow")("Twitter"),
    )

  def sfenAnalysisLink(variant: Variant, sfen: Sfen)(implicit lang: Lang) =
    a(href := routes.UserAnalysis.parseArg(s"${variant.key}/${sfen.value.replace(" ", "_")}"))(
      trans.analysis(),
    )
}
