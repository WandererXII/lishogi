package lishogi.storm

import play.api.libs.json._

import lishogi.common.Json._
import lishogi.user.User
import org.joda.time.format.DateTimeFormat

final class StormJson(sign: StormSign) {

  import StormJson._

  def apply(puzzles: List[StormPuzzle], user: Option[User]): JsObject = Json
    .obj(
      "puzzles"      -> puzzles,
      "notAnExploit" -> StormForm.notAnExploit
    )
    .add("key" -> user.map(sign.getPrev))

  def pref(p: lishogi.pref.Pref) =
    Json.obj(
      "coords"          -> p.coords,
      "destination"     -> p.destination,
      "dropDestination" -> p.dropDestination,
      "moveEvent"       -> p.moveEvent,
      "highlight"       -> p.highlight,
      "pieceNotation"   -> p.pieceNotation,
      "is3d"            -> p.is3d
    )

  def newHigh(n: Option[StormHigh.NewHigh]) =
    Json
      .obj()
      .add("newHigh" -> n.map { nh =>
        Json.obj(
          "key"  -> nh.key,
          "prev" -> nh.previous
        )
      })

  def apiDashboard(high: StormHigh, days: List[StormDay]) = Json.obj(
    "high" -> high,
    "days" -> days
  )

}

object StormJson {

  import lishogi.puzzle.JsonView.puzzleIdWrites

  implicit val highWrites: OWrites[StormHigh] = Json.writes[StormHigh]

  private val dateFormat = DateTimeFormat forPattern "Y/M/d"

  implicit val dayIdWrites: Writes[StormDay.Id] = Writes { id =>
    JsString(dateFormat print id.day.toDate)
  }
  implicit val dayWrites: OWrites[StormDay] = Json.writes[StormDay]

  implicit val puzzleWrites: OWrites[StormPuzzle] = OWrites { p =>
    Json.obj(
      "id"     -> p.id,
      "fen"    -> p.fen,
      "line"   -> p.line.toList.map(_.uci).mkString(" "),
      "rating" -> p.rating
    )
  }
}
