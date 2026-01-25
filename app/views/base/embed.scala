package views.html.base

import lila.app.templating.Environment._
import lila.app.ui.EmbedConfig
import lila.app.ui.ScalatagsTemplate._

object embed {

  import EmbedConfig.implicits._

  def apply(
      title: String,
      moreCss: Frag = emptyFrag,
      moreJs: Frag = emptyFrag,
      variant: shogi.variant.Variant = shogi.variant.Standard,
  )(body: Modifier*)(implicit config: EmbedConfig) =
    frag(
      layout.bits.doctype,
      html(
        st.lang := lila.i18n.languageCode(config.lang),
        cls     := layout.bits.backgroundClass(config.bg, config.customBg),
        style   := layout.bits.cssVariables(none, config.customTheme, config.customBg, none),
      )(
        head(
          layout.bits.charset,
          layout.bits.viewport,
          layout.bits.metaCsp(basicCsp withNonce config.nonce),
          st.headTitle(title),
          pieceSpriteByVariant(variant),
          cssTag("common.variables"),
          (config.bg == "custom") option cssTag("common.custom"),
          moreCss,
          embedJsUnsafe(layout.bits.windowLishogi, config.nonce.some),
          vendorJsTag("shogiground", "shogiground.min.js"),
          moreJs,
        ),
        st.body(cls := s"base highlight ${config.theme}")(
          layout.dataSoundSet        := lila.pref.SoundSet.silent.key,
          layout.dataClockSoundSet   := lila.pref.ClockSoundSet.silent.key,
          layout.dataAssetUrl        := env.net.assetBaseUrl,
          layout.dataAssetVersion    := assetVersion.value,
          layout.dataTheme           := config.bg,
          layout.dataPieceSet        := config.pieceSet.key,
          layout.dataChuPieceSet     := config.chuPieceSet.key,
          layout.dataKyoPieceSet     := config.kyoPieceSet.key,
          layout.dataDobutsuPieceSet := config.dobutsuPieceSet.key,
          layout.dataNotation        := config.notation,
          layout.dataColorName       := config.colorName.toString,
          body,
        ),
      ),
    )

  private def pieceSpriteByVariant(
      variant: shogi.variant.Variant,
  )(implicit config: EmbedConfig): Frag =
    if (variant.chushogi) chuPieceSprite(config.chuPieceSet)
    else if (variant.kyotoshogi) kyoPieceSprite(config.kyoPieceSet)
    else if (variant.dobutsu) dobutsuPieceSprite(config.dobutsuPieceSet)
    else defaultPieceSprite(config.pieceSet)
}
