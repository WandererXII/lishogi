package views.html.base

import lishogi.app.templating.Environment._
import lishogi.app.ui.EmbedConfig
import lishogi.app.ui.ScalatagsTemplate._
import lishogi.pref.SoundSet

object embed {

  import EmbedConfig.implicits._

  def apply(title: String, cssModule: String)(body: Modifier*)(implicit config: EmbedConfig) =
    frag(
      layout.bits.doctype,
      layout.bits.htmlTag(config.lang)(
        head(
          layout.bits.charset,
          layout.bits.viewport,
          layout.bits.metaCsp(basicCsp withNonce config.nonce),
          st.headTitle(title),
          layout.bits.pieceSprite(lishogi.pref.PieceSet.default),
          cssTagWithTheme(cssModule, config.bg)
        ),
        st.body(cls := s"base highlight ${config.board}")(
          layout.dataSoundSet := SoundSet.silent.key,
          layout.dataAssetUrl := env.net.assetBaseUrl,
          layout.dataAssetVersion := assetVersion.value,
          layout.dataTheme := config.bg,
          body
        )
      )
    )
}
