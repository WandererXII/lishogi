package views.html.analyse

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._

object bits {

  val dataPanel = attr("data-panel")

  def layout(
      title: String,
      moreCss: Frag = emptyFrag,
      moreJs: Frag = emptyFrag,
      openGraph: Option[lila.app.ui.OpenGraph] = none,
      variant: Option[shogi.variant.Variant] = none,
  )(body: Frag)(implicit ctx: Context): Frag =
    views.html.base.layout(
      title = title,
      moreCss = moreCss,
      moreJs = moreJs,
      openGraph = openGraph,
      variant = variant,
      shogiground = false,
      robots = false,
      zoomable = true,
      csp = defaultCsp.withWebAssembly.withPeer.some,
    )(body)
}
