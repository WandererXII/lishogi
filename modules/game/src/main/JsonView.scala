package lila.game

import play.api.libs.json._

import shogi.Clock
import shogi.Color
import shogi.format.forsyth.Sfen

import lila.common.Json._
import lila.common.LightUser

final class JsonView(rematches: Rematches) {

  import JsonView._

  def apply(game: Game) =
    Json
      .obj(
        "id"            -> game.id,
        "variant"       -> game.variant,
        "speed"         -> game.speed.key,
        "perf"          -> PerfPicker.key(game),
        "rated"         -> game.rated,
        "initialSfen"   -> (game.initialSfen | game.variant.initialSfen),
        "sfen"          -> game.shogi.toSfen,
        "player"        -> game.turnColor,
        "plies"         -> game.plies,
        "startedAtStep" -> game.shogi.startedAtStep,
        "startedAtPly"  -> game.shogi.startedAtPly,
        "source"        -> game.source,
        "status"        -> game.status,
        "createdAt"     -> game.createdAt,
      )
      .add("boosted" -> game.boosted)
      .add("tournamentId" -> game.tournamentId)
      .add("winner" -> game.winnerColor)
      .add("lastMove" -> game.lastUsiStr)
      .add("check" -> game.situation.check)
      .add("rematch" -> rematches.of(game.id))
      .add("postGameStudy" -> game.postGameStudy)

  def ownerPreview(pov: Pov)(lightUserSync: LightUser.GetterSync) =
    Json
      .obj(
        "fullId"   -> pov.fullId,
        "gameId"   -> pov.gameId,
        "sfen"     -> pov.game.situation.toSfen,
        "color"    -> pov.color.name,
        "lastMove" -> ~pov.game.lastUsiStr,
        "source"   -> pov.game.source,
        "status"   -> pov.game.status,
        "variant" -> Json.obj(
          "key"  -> pov.game.variant.key,
          "name" -> pov.game.variant.name,
        ),
        "speed"    -> pov.game.speed.key,
        "perf"     -> lila.game.PerfPicker.key(pov.game),
        "rated"    -> pov.game.rated,
        "hasMoved" -> pov.hasMoved,
        "opponent" -> Json
          .obj(
            "id" -> pov.opponent.userId,
            "username" -> lila.game.Namer
              .playerTextBlocking(pov.opponent, withRating = false)(
                lightUserSync,
                lila.i18n.defaultLang,
              ),
          )
          .add("rating" -> pov.opponent.rating)
          .add("ai" -> pov.opponent.aiLevel)
          .add("aiCode" -> pov.opponent.aiCode),
        "isMyTurn" -> pov.isMyTurn,
      )
      .add("secondsLeft" -> pov.remainingSeconds)
      .add("tournamentId" -> pov.game.tournamentId)
      .add("winner" -> pov.game.winnerColor)
}

object JsonView {

  implicit val statusWrites: OWrites[shogi.Status] = OWrites { s =>
    Json.obj(
      "id"   -> s.id,
      "name" -> s.name,
    )
  }

  implicit val crosstableResultWrites: OWrites[Crosstable.Result] = Json.writes[Crosstable.Result]

  implicit val crosstableUsersWrites: OWrites[Crosstable.Users] = OWrites[Crosstable.Users] {
    users =>
      JsObject(users.toList.map { u =>
        u.id -> JsNumber(u.score / 10d)
      })
  }

  implicit val crosstableWrites: OWrites[Crosstable] = OWrites[Crosstable] { c =>
    Json.obj(
      "users"   -> c.users,
      "nbGames" -> c.nbGames,
      // "results" -> c.results
    )
  }

  // implicit val matchupWrites = OWrites[Crosstable.Matchup] { m =>
  //   Json.obj(
  //     "users" -> m.users,
  //     "nbGames" -> m.users.nbGames
  //   )
  // }
  // implicit val crosstableWithMatchupWrites = Json.writes[Crosstable.WithMatchup]

  implicit val blursWriter: OWrites[Blurs] = OWrites { blurs =>
    Json.obj(
      "nb"   -> blurs.nb,
      "bits" -> blurs.binaryString,
    )
  }

  implicit val variantWriter: OWrites[shogi.variant.Variant] = OWrites { v =>
    Json.obj(
      "key"  -> v.key,
      "name" -> v.name,
    )
  }

  implicit val clockWriter: OWrites[Clock] = OWrites { c =>
    val senteClock = c currentClockFor Color.Sente
    val goteClock  = c currentClockFor Color.Gote
    Json.obj(
      "running"   -> c.isRunning,
      "initial"   -> c.limitSeconds,
      "increment" -> c.incrementSeconds,
      "byoyomi"   -> c.byoyomiSeconds,
      "periods"   -> c.periodsTotal,
      "sPeriods"  -> senteClock.periods,
      "gPeriods"  -> goteClock.periods,
      "sente"     -> senteClock.time.toSeconds,
      "gote"      -> goteClock.time.toSeconds,
      "emerg"     -> c.config.emergSeconds,
    )
  }

  implicit val correspondenceWriter: OWrites[CorrespondenceClock] = OWrites { c =>
    Json.obj(
      "daysPerTurn" -> c.daysPerTurn,
      "increment"   -> c.increment,
      "sente"       -> c.senteTime,
      "gote"        -> c.goteTime,
    )
  }

  implicit val divisionWriter: OWrites[shogi.Division] = OWrites { o =>
    Json.obj(
      "middle" -> o.middle,
      "end"    -> o.end,
    )
  }

  implicit val sourceWriter: Writes[Source] = Writes { s =>
    JsString(s.name)
  }

  implicit val colorWrites: Writes[Color] = Writes { c =>
    JsString(c.name)
  }

  implicit val sfenWrites: Writes[Sfen] = Writes { f =>
    JsString(f.value)
  }
}
