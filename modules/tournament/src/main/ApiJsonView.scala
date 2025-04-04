package lila.tournament

import play.api.i18n.Lang
import play.api.libs.json._

import lila.common.Json.jodaWrites
import lila.rating.PerfType
import lila.user.LightUserApi

final class ApiJsonView(lightUserApi: LightUserApi)(implicit
    ec: scala.concurrent.ExecutionContext,
) {

  import JsonView._

  def featured(tournaments: List[Tournament])(implicit lang: Lang): Fu[JsObject] =
    tournaments.map(fullJson).sequenceFu map { objs =>
      Json.obj("featured" -> objs)
    }

  def calendar(tournaments: List[Tournament])(implicit lang: Lang): JsObject =
    Json.obj(
      "since"       -> tournaments.headOption.map(_.startsAt.withTimeAtStartOfDay),
      "to"          -> tournaments.lastOption.map(_.finishesAt.withTimeAtStartOfDay plusDays 1),
      "tournaments" -> JsArray(tournaments.map(baseJson)),
    )

  def baseJson(tour: Tournament)(implicit lang: Lang): JsObject =
    Json
      .obj(
        "id"        -> tour.id,
        "createdBy" -> tour.createdBy,
        "system"    -> tour.format.key,
        "minutes"   -> tour.minutes,
        "clock"     -> tour.timeControl,
        "rated"     -> tour.mode.rated,
        "fullName"  -> tour.trans,
        "nbPlayers" -> tour.nbPlayers,
        "variant" -> Json.obj(
          "key"  -> tour.variant.key,
          "name" -> tour.variant.name,
        ),
        "startsAt"   -> tour.startsAt,
        "finishesAt" -> tour.finishesAt,
        "status"     -> tour.status.id,
        "perf"       -> perfJson(tour.perfType),
      )
      .add("secondsToStart", tour.secondsToStart.some.filter(0 <))
      .add("hasMaxRating", tour.conditions.maxRating.isDefined)
      .add("private", tour.isPrivate)
      .add("position", tour.position.map(sfen => positionJson(sfen)))
      .add("schedule", tour.schedule map scheduleJson)
      .add(
        "teamBattle",
        tour.teamBattle.map { battle =>
          Json.obj(
            "teams"     -> battle.teams,
            "nbLeaders" -> battle.nbLeaders,
          )
        },
      )

  def fullJson(tour: Tournament)(implicit lang: Lang): Fu[JsObject] =
    (tour.winnerId ?? lightUserApi.async) map { winner =>
      baseJson(tour).add("winner" -> winner.map(userJson))
    }

  private def userJson(u: lila.common.LightUser) =
    Json.obj(
      "id"    -> u.id,
      "name"  -> u.name,
      "title" -> u.title,
    )

  private val perfPositions: Map[PerfType, Int] = {
    import PerfType._
    List(Bullet, Blitz, Rapid, Classical, UltraBullet) ::: variants
  }.zipWithIndex.toMap

  private def perfJson(p: PerfType)(implicit lang: Lang) =
    Json.obj(
      "icon"     -> p.iconChar.toString,
      "key"      -> p.key,
      "name"     -> p.trans,
      "position" -> ~perfPositions.get(p),
    )

}
