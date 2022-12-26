package lila.game

import play.api.libs.json._

import shogi.format.forsyth.Sfen
import shogi.{ Clock, Color }
import lila.common.Json.jodaWrites

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
        "startedAtMove" -> game.shogi.startedAtMove,
        "startedAtPly"  -> game.shogi.startedAtPly,
        "source"        -> game.source,
        "status"        -> game.status,
        "createdAt"     -> game.createdAt
      )
      .add("boosted" -> game.boosted)
      .add("tournamentId" -> game.tournamentId)
      .add("winner" -> game.winnerColor)
      .add("lastMove" -> game.lastMoveKeys)
      .add("check" -> game.situation.check)
      .add("rematch" -> rematches.of(game.id))
}

object JsonView {

  implicit val statusWrites: OWrites[shogi.Status] = OWrites { s =>
    Json.obj(
      "id"   -> s.id,
      "name" -> s.name
    )
  }

  implicit val crosstableResultWrites = Json.writes[Crosstable.Result]

  implicit val crosstableUsersWrites = OWrites[Crosstable.Users] { users =>
    JsObject(users.toList.map { u =>
      u.id -> JsNumber(u.score / 10d)
    })
  }

  implicit val crosstableWrites = OWrites[Crosstable] { c =>
    Json.obj(
      "users"   -> c.users,
      "nbGames" -> c.nbGames
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
      "bits" -> blurs.binaryString
    )
  }

  implicit val variantWriter: OWrites[shogi.variant.Variant] = OWrites { v =>
    Json.obj(
      "key"   -> v.key,
      "name"  -> v.name,
      "short" -> v.shortName
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
      "emerg"     -> c.config.emergSeconds
    )
  }

  implicit val correspondenceWriter: OWrites[CorrespondenceClock] = OWrites { c =>
    Json.obj(
      "daysPerTurn" -> c.daysPerTurn,
      "increment"   -> c.increment,
      "sente"       -> c.senteTime,
      "gote"        -> c.goteTime
    )
  }

  implicit val openingWriter: OWrites[shogi.opening.FullOpening.AtPly] = OWrites { o =>
    Json.obj(
      "japanese" -> o.opening.japanese,
      "english"  -> o.opening.english,
      "ply"      -> o.ply
    )
  }

  implicit val divisionWriter: OWrites[shogi.Division] = OWrites { o =>
    Json.obj(
      "middle" -> o.middle,
      "end"    -> o.end
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
