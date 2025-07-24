package lila.challenge

import play.api.libs.json._

import lila.socket.Socket.SocketVersion
import lila.socket.UserLagCache

final class JsonView(
    baseUrl: lila.common.config.BaseUrl,
    getLightUser: lila.common.LightUser.GetterSync,
    isOnline: lila.socket.IsOnline,
    getTourName: lila.tournament.GetTourName,
    getArrName: lila.tournament.GetArrName,
) {

  import Challenge._

  import lila.game.JsonView._

  implicit private val RegisteredWrites: OWrites[Challenger.Registered] =
    OWrites[Challenger.Registered] { r =>
      val light = getLightUser(r.id)
      Json
        .obj(
          "id"     -> r.id,
          "name"   -> light.fold(r.id)(_.name),
          "title"  -> light.map(_.title),
          "rating" -> r.rating.int,
        )
        .add("provisional" -> r.rating.provisional)
        .add("patron" -> light.??(_.isPatron))
        .add("online" -> isOnline(r.id))
        .add("lag" -> UserLagCache.getLagRating(r.id))
    }

  def apply(a: AllChallenges): JsObject =
    Json.obj(
      "in"  -> a.in.map(apply(Direction.In.some)),
      "out" -> a.out.map(apply(Direction.Out.some)),
    )

  def show(challenge: Challenge, socketVersion: SocketVersion, direction: Option[Direction]) =
    Json.obj(
      "challenge"     -> apply(direction)(challenge),
      "socketVersion" -> socketVersion,
    )

  def api(
      challenge: Challenge,
      socketVersion: SocketVersion,
      direction: Option[Direction],
  ) =
    (apply(direction)(challenge)) ++ Json.obj("socketVersion" -> socketVersion)

  def apply(direction: Option[Direction])(c: Challenge): JsObject =
    Json
      .obj(
        "id"         -> c.id,
        "url"        -> s"$baseUrl/${c.id}",
        "status"     -> c.status.name,
        "challenger" -> c.challengerUser,
        "destUser"   -> c.destUser,
        "variant"    -> c.variant,
        "rated"      -> c.mode.rated,
        "speed"      -> c.speed.key,
        "timeControl" -> (c.timeControl match {
          case TimeControl.Clock(clock) =>
            Json.obj(
              "type"      -> "clock",
              "limit"     -> clock.limitSeconds,
              "increment" -> clock.incrementSeconds,
              "byoyomi"   -> clock.byoyomiSeconds,
              "periods"   -> clock.periodsTotal,
              "show"      -> clock.show,
            )
          case TimeControl.Correspondence(d) =>
            Json.obj(
              "type"        -> "correspondence",
              "daysPerTurn" -> d,
            )
          case TimeControl.Unlimited => Json.obj("type" -> "unlimited")
        }),
        "color" -> c.colorChoice.toString.toLowerCase,
        "perf" -> Json.obj(
          "icon" -> iconChar(c).toString,
          "name" -> c.perfType.key,
        ),
      )
      .add(
        "tourInfo" -> c.tourInfo.map(ti =>
          Json
            .obj(
              "tourId" -> ti.tournamentId,
              "arrId"  -> ti.arrangementId,
            )
            .add("tourName" -> getTourName(ti.tournamentId, lila.i18n.defaultLang))
            .add("arrName" -> ti.withName ?? getArrName(ti.arrangementId)),
        ),
      )
      .add("direction" -> direction.map(_.name))
      .add("rematchOf" -> c.rematchOf)
      .add("initialSfen" -> c.initialSfen)

  private def iconChar(c: Challenge) =
    if (c.initialSfen.isDefined) '*'
    else c.perfType.iconChar

}
