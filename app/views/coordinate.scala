package views.html

import play.api.libs.json.Json

import lishogi.api.Context
import lishogi.app.templating.Environment._
import lishogi.app.ui.ScalatagsTemplate._
import lishogi.common.String.html.safeJsonValue
import lishogi.pref.Pref.Color
import play.api.i18n.Lang

import controllers.routes

object coordinate {

  def home(scoreOption: Option[lishogi.coordinate.Score])(implicit ctx: Context) =
    views.html.base.layout(
      title = trans.coordinates.coordinateTraining.txt(),
      moreCss = cssTag("coordinate"),
      moreJs = frag(
        jsTag("vendor/sparkline.min.js"),
        jsAt("compiled/coordinate.js")
      ),
      openGraph = lishogi.app.ui
        .OpenGraph(
          title = "Shogi board coordinates trainer",
          url = s"$netBaseUrl${routes.Coordinate.home().url}",
          description =
            "Knowing the shogi board coordinates is a very important shogi skill. A square name appears on the board and you must click on the correct square."
        )
        .some,
      zoomable = true
    )(
      main(
        id := "trainer",
        cls := "coord-trainer training init",
        attr("data-color-pref") := ctx.pref.coordColorName,
        attr("data-score-url") := ctx.isAuth.option(routes.Coordinate.score().url)
      )(
        div(cls := "coord-trainer__side")(
          div(cls := "box")(
            h1(trans.coordinates.coordinates()),
            if (ctx.isAuth) scoreOption.map { score =>
              div(cls := "scores")(scoreCharts(score))
            }
          ),
          form(cls := "color buttons", action := routes.Coordinate.color())(
            st.group(cls := "radio")(
              List(Color.GOTE, Color.RANDOM, Color.SENTE).map { id =>
                div(
                  input(
                    tpe := "radio",
                    st.id := s"coord_color_$id",
                    name := "color",
                    value := id,
                    (id == ctx.pref.coordColor) option checked
                  ),
                  label(`for` := s"coord_color_$id", cls := s"color color_$id")(i)
                )
              }
            )
          )
        ),
        div(cls := "coord-trainer__board main-board")(
          div(cls := "next_coord", id := "next_coord0"),
          div(cls := "next_coord", id := "next_coord1"),
          shogigroundBoard
        ),
        div(cls := "coord-trainer__table")(
          div(cls := "explanation")(
            p(trans.coordinates.knowingTheShogiBoard()),
            ul(
              li(trans.coordinates.mostShogiCourses()),
              li(trans.coordinates.talkToYourShogiFriends()),
              li(trans.coordinates.youCanAnalyseAGameMoreEffectively())
            ),
            p(trans.coordinates.aSquareNameAppears())
          ),
          div(cls := "box current-status")(
            h1(trans.storm.score()),
            div(cls := "coord-trainer__score")(0)
          ),
          div(cls := "box current-status")(
            h1(trans.time()),
            div(cls := "coord-trainer__timer")(30.0)
          )
        ),
        div(cls := "coord-trainer__button")(
          div(cls := "coord-start")(
            button(cls := "start button button-fat")(trans.coordinates.startTraining())
          ),
          div(cls := "current-color")
        ),
        div(cls := "coord-trainer__progress")(div(cls := "progress_bar"))
      )
    )

  def scoreCharts(score: lishogi.coordinate.Score)(implicit ctx: Context) =
    frag(
      List(
        (trans.coordinates.averageScoreAsWhiteX, score.gote),
        (trans.coordinates.averageScoreAsBlackX, score.sente)
      ).map { case (averageScoreX, s) =>
        div(cls := "chart_container")(
          s.nonEmpty option frag(
            p(averageScoreX(raw(s"""<strong>${"%.2f".format(s.sum.toDouble / s.size)}</strong>"""))),
            div(cls := "user_chart", attr("data-points") := safeJsonValue(Json toJson s))
          )
        )
      }
    )
}
