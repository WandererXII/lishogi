package views.html.coordinate

import play.api.libs.json.Json

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._

object bits {

  def coordinateConfig(scoreOption: Option[lila.coordinate.Score])(implicit ctx: Context) = Json.obj(
    "i18n"       -> i18nJsObject(i18nKeys),
    "resizePref" -> ctx.pref.resizeHandle,
    "scores" -> Json.obj(
      "findSquare" -> Json.obj(
        "sente" -> scoreOption.??(_.sente),
        "gote" -> scoreOption.??(_.gote)
      ),
      "nameSquare" -> Json.obj(
        "sente" -> scoreOption.??(_.senteNameSquare),
        "gote" -> scoreOption.??(_.goteNameSquare)
      )
    )
  )

  private val i18nKeys = List(
    trans.coordinates.aSquareIsHighlighted,
    trans.coordinates.aSquareNameAppears,
    trans.coordinates.youHaveThirtySeconds,
    trans.coordinates.goAsLongAsYouWant,
    trans.coordinates.averageScoreAsBlackX,
    trans.coordinates.averageScoreAsWhiteX,
    trans.coordinates.coordinates,
    trans.coordinates.knowingTheShogiBoard,
    trans.coordinates.mostShogiCourses,
    trans.coordinates.startTraining,
    trans.coordinates.talkToYourShogiFriends,
    trans.coordinates.youCanAnalyseAGameMoreEffectively,
    trans.coordinates.findSquare,
    trans.coordinates.nameSquare,
    trans.storm.score,
    trans.study.back,
    trans.time,
    trans.asBlack,
    trans.asWhite,
    trans.randomColor
  ).map(_.key)
}
