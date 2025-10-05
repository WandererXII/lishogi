package lila.pref

import play.api.mvc.RequestHeader

object RequestPref {

  import Pref.default

  def queryParamOverride(req: RequestHeader)(pref: Pref): Pref =
    queryParam(req, "bg").fold(pref) { bg =>
      if (bg == Background.dark.key || bg == Background.light.key)
        pref.copy(
          background = bg,
        )
      else pref
    }

  def fromRequest(req: RequestHeader, languageNotation: Boolean = true): Pref = {

    def paramOrSession(name: String): Option[String] =
      queryParam(req, name) orElse req.session.get(name)

    val bg       = paramOrSession("bg") | "dark"
    val theme    = paramOrSession("theme") | default.theme
    val pieceSet = paramOrSession("pieceSet") | default.pieceSet

    default.copy(
      background = bg,
      theme = theme,
      pieceSet = pieceSet,
      chuPieceSet = paramOrSession("chuPieceSet") | default.chuPieceSet,
      kyoPieceSet = paramOrSession("kyoPieceSet") | default.kyoPieceSet,
      soundSet = paramOrSession("soundSet") | default.soundSet,
      clockSoundSet = paramOrSession("clockSoundSet") | default.clockSoundSet,
      bgImg = paramOrSession("bgImg"),
      notation =
        paramOrSession("notation").flatMap(_.toIntOption) | defaultNotation(req, languageNotation),
      thickGrid = paramOrSession("thickGrid").flatMap(_.toIntOption) | default.thickGrid,
    )
  }

  private def queryParam(req: RequestHeader, name: String): Option[String] =
    req.queryString.get(name).flatMap(_.headOption).filter { v =>
      v.nonEmpty && v != "auto"
    }

  private def defaultNotation(req: RequestHeader, languageNotation: Boolean): Int =
    if (languageNotation && req.acceptLanguages.headOption.exists(_.language == "ja"))
      Notations.japanese.index
    else default.notation

}
