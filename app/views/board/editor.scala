package views.html.board

import play.api.libs.json.Json

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.common.String.html.safeJsonValue

import controllers.routes

object editor {

  def apply(
      sit: shogi.Situation
  )(implicit ctx: Context) =
    views.html.base.layout(
      title = trans.boardEditor.txt(),
      moreJs = frag(
        jsModule("editor"),
        embedJsUnsafe(
          s"""var data=${safeJsonValue(jsData(sit))};
LishogiEditor(document.getElementById('board-editor'), data);"""
        )
      ),
      moreCss = frag(
        cssTag("editor"),
        sit.variant.chushogi option views.html.base.layout.bits.chuPieceSprite,
        sit.variant.kyotoshogi option views.html.base.layout.bits.kyoPieceSprite
      ),
      shogiground = false,
      zoomable = true,
      openGraph = lila.app.ui
        .OpenGraph(
          title = trans.boardEditor.txt(),
          url = s"$netBaseUrl${routes.Editor.index.url}",
          description = trans.editorDescription.txt()
        )
        .some,
      canonicalPath = lila.common.CanonicalPath(routes.Editor.index).some,
      withHrefLangs = lila.i18n.LangList.All.some
    )(
      main(id := "board-editor")(
        div(cls   := s"board-editor variant-${sit.variant.key}")(
          div(cls := "board-editor__tools"),
          div(cls := "main-board")(shogigroundEmpty(sit.variant, shogi.Sente)),
          div(cls := "board-editor__side")
        )
      )
    )

  def jsData(
      sit: shogi.Situation
  )(implicit ctx: Context) =
    Json.obj(
      "sfen"    -> sit.toSfen.truncate.value,
      "variant" -> sit.variant.key,
      "baseUrl" -> s"$netBaseUrl${routes.Editor.index}",
      "pref" -> Json
        .obj(
          "animation"          -> ctx.pref.animationMillis,
          "coords"             -> ctx.pref.coords,
          "moveEvent"          -> ctx.pref.moveEvent,
          "resizeHandle"       -> ctx.pref.resizeHandle,
          "highlightLastDests" -> ctx.pref.highlightLastDests,
          "squareOverlay"      -> ctx.pref.squareOverlay
        ),
      "i18n" -> i18nJsObject(i18nKeyes)
    )

  private val i18nKeyes = List(
    trans.black,
    trans.white,
    trans.sente,
    trans.gote,
    trans.shitate,
    trans.uwate,
    trans.setTheBoard,
    trans.boardEditor,
    trans.startPosition,
    trans.clearBoard,
    trans.invalidSfen,
    trans.fillXHand,
    trans.flipBoard,
    trans.loadPosition,
    trans.popularOpenings,
    trans.handicaps,
    trans.xPlays,
    trans.variant,
    trans.continueFromHere,
    trans.playWithTheMachine,
    trans.playWithAFriend,
    trans.analysis,
    trans.toStudy,
    trans.standard,
    trans.minishogi,
    trans.chushogi,
    trans.annanshogi,
    trans.kyotoshogi,
    trans.checkshogi
  ).map(_.key)
}
