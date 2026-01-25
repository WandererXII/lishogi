package lila.app
package ui

import play.api.i18n.Lang
import play.api.mvc.RequestHeader

import lila.common.Nonce
import lila.pref.CustomBackground
import lila.pref.CustomTheme

case class EmbedConfig(
    bg: String,
    customBg: Option[CustomBackground],
    theme: String,
    customTheme: Option[CustomTheme],
    pieceSet: lila.pref.PieceSet,
    chuPieceSet: lila.pref.PieceSet,
    kyoPieceSet: lila.pref.PieceSet,
    dobutsuPieceSet: lila.pref.PieceSet,
    notation: String,
    colorName: Int,
    lang: Lang,
    req: RequestHeader,
    nonce: Nonce,
)

object EmbedConfig {

  object implicits {
    implicit def configLang(implicit config: EmbedConfig): Lang         = config.lang
    implicit def configReq(implicit config: EmbedConfig): RequestHeader = config.req
  }

  def apply(reqHeader: RequestHeader): EmbedConfig = {
    implicit val req = reqHeader
    val customTheme = CustomTheme(
      boardColor = get("t-boardColor") | CustomTheme.default.boardColor,
      boardImg = get("t-boardImg") | CustomTheme.default.boardImg,
      gridColor = get("t-gridColor") | CustomTheme.default.gridColor,
      gridWidth = get("t-gridWidth").flatMap(_.toIntOption) | CustomTheme.default.gridWidth,
      handsColor = get("t-handsColor") | CustomTheme.default.handsColor,
      handsImg = get("t-handsImg") | CustomTheme.default.handsImg,
    ).some.filterNot(_ == CustomTheme.default)

    val customBg = get("bg-bgPage") map { bgPage =>
      CustomBackground(
        light = get("bg-light").isDefined,
        bgPage = bgPage,
        bgImg = "",
        font = ~get("bg-font"),
        accent = ~get("bg-accent"),
        primary = ~get("bg-primary"),
        secondary = ~get("bg-secondary"),
        brag = ~get("bg-brag"),
        green = ~get("bg-green"),
        red = ~get("bg-red"),
      )
    }
    val bg = get("bg") | "auto"

    val pieceSet = get("pieceSet")

    EmbedConfig(
      bg = if (bg == "auto" && customBg.isDefined) "custom" else lila.pref.Background(bg).key,
      customBg = customBg,
      theme = lila.pref.Theme(~get("theme")).cssClass,
      customTheme = customTheme,
      pieceSet = lila.pref.PieceSet(~pieceSet),
      chuPieceSet = lila.pref.ChuPieceSet(get("chuPieceSet") | ~pieceSet),
      kyoPieceSet = lila.pref.KyoPieceSet(get("kyoPieceSet") | ~pieceSet),
      dobutsuPieceSet = lila.pref.DobutsuPieceSet(get("dobutsuPieceSet") | ~pieceSet),
      notation = ~get("notation"),
      colorName = ~get("colorName").flatMap(_.toIntOption),
      lang = get("lang")
        .flatMap(lila.i18n.I18nLangPicker.byQuery) | lila.i18n.I18nLangPicker(req, none),
      req = req,
      nonce = Nonce.random,
    )
  }

  private def get(name: String)(implicit req: RequestHeader): Option[String] =
    req.queryString get name flatMap (_.headOption) filter (_.nonEmpty)
}
