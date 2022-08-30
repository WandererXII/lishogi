package views.html.puzzle

import controllers.routes
import play.api.libs.json.{ JsObject, Json }

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.common.String.html.safeJsonValue

object show {

  def apply(
      puzzle: lila.puzzle.Puzzle,
      data: JsObject,
      pref: JsObject,
      difficulty: Option[lila.puzzle.PuzzleDifficulty] = None
  )(implicit
      ctx: Context
  ) =
    views.html.base.layout(
      title = trans.puzzles.txt(),
      moreCss = cssTag("puzzle"),
      moreJs = frag(
        jsModule("puzzle", true),
        embedJsUnsafe(s"""$$(function() {
          LishogiPuzzle(${safeJsonValue(
            Json
              .obj(
                "data" -> data,
                "pref" -> pref,
                "i18n" -> bits.jsI18n
              )
              .add("themes" -> ctx.isAuth.option(bits.jsonThemes))
              .add("difficulty" -> difficulty.map(_.key))
          )})})""")
      ),
      csp = defaultCsp.withWebAssembly.some,
      shogiground = false,
      openGraph = lila.app.ui
        .OpenGraph(
          image = cdnUrl(routes.Export.puzzleThumbnail(puzzle.id.value).url).some,
          title = s"Shogi tactic #${puzzle.id}",
          url = s"$netBaseUrl${routes.Puzzle.show(puzzle.id.value).url}",
          description = s"Lishogi tactic trainer: " + puzzle.color
            .fold(
              trans.puzzle.findTheBestMoveForBlack,
              trans.puzzle.findTheBestMoveForWhite
            )
            .txt() + s" Played by ${puzzle.plays} players."
        )
        .some,
      zoomable = true,
      playing = true
    ) {
      main(cls := "puzzle")(
        st.aside(cls := "puzzle__side")(
          div(cls    := "puzzle__side__metas")
        ),
        div(cls := "puzzle__board main-board")(shogigroundBoard(shogi.variant.Standard, puzzle.color.some)),
        sgHandTop,
        div(cls := "puzzle__tools"),
        sgHandBottom,
        div(cls := "puzzle__controls")
      )
    }
}
