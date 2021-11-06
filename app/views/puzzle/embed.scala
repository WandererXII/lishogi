package views.html.puzzle

import controllers.routes
import play.api.i18n.Lang

import lishogi.app.templating.Environment._
import lishogi.app.ui.EmbedConfig
import lishogi.app.ui.ScalatagsTemplate._
import lishogi.puzzle.DailyPuzzle

object embed {

  import EmbedConfig.implicits._

  def apply(daily: DailyPuzzle.Html)(implicit config: EmbedConfig) =
    views.html.base.embed(
      title = "lishogi.org shogi puzzle",
      cssModule = "tv.embed"
    )(
      dailyLink(daily)(config.lang)(
        targetBlank,
        id := "daily-puzzle",
        cls := "embedded"
      ),
      jQueryTag,
      jsAt("javascripts/vendor/shogiground.min.js", false),
      jsAt("compiled/puzzle.js", false)
    )

  def dailyLink(daily: DailyPuzzle.Html)(implicit lang: Lang) = a(
    href := routes.Puzzle.daily,
    title := trans.puzzle.clickToSolve.txt()
  )(
    raw(daily.html),
    div(cls := "vstext")(
      trans.puzzleOfTheDay(),
      br,
      daily.color.fold(trans.blackPlays, trans.whitePlays)()
    )
  )
}
