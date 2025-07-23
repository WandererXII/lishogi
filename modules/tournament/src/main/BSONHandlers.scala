package lila.tournament

import org.joda.time.DateTime
import reactivemongo.api.bson._

import shogi.Mode
import shogi.format.forsyth.Sfen
import shogi.variant.Variant

import lila.db.BSON
import lila.db.dsl._
import lila.rating.PerfType
import lila.user.User.lishogiId

object BSONHandlers {

  implicit private[tournament] val formatBSONHandler: BSONHandler[Format] = tryHandler[Format](
    { case BSONString(v) => Format.byKey(v) toTry s"No such format: $v" },
    x => BSONString(x.key),
  )

  implicit private[tournament] val statusBSONHandler: BSONHandler[Status] = tryHandler[Status](
    { case BSONInteger(v) => Status(v) toTry s"No such status: $v" },
    x => BSONInteger(x.id),
  )

  implicit private[tournament] val pointsBSONHandler: BSONHandler[Arrangement.Points] = {
    val intReader = collectionReader[List, Int]
    tryHandler[Arrangement.Points](
      { case arr: BSONArray =>
        intReader.readTry(arr).filter(_.length == 3) map { p =>
          Arrangement.Points(p(0), p(1), p(2))
        }
      },
      points => BSONArray(points.loss, points.draw, points.win),
    )
  }

  implicit private[tournament] val scheduleFreqHandler: BSONHandler[Schedule.Freq] =
    quickHandler[Schedule.Freq](
      { case BSONString(v) => Schedule.Freq(v).getOrElse(Schedule.Freq.Unique) },
      x => BSONString(x.key),
    )

  implicit private[tournament] val scheduleSpeedHandler: BSONHandler[Schedule.Speed] =
    tryHandler[Schedule.Speed](
      { case BSONString(v) => Schedule.Speed(v) toTry s"No such speed: $v" },
      x => BSONString(x.key),
    )

  implicit val timeControlBSONHandler: BSONHandler[TimeControl] = tryHandler[TimeControl](
    { case doc: BSONDocument =>
      doc.getAsOpt[Int]("days") match {
        case Some(d) =>
          scala.util.Success(TimeControl.Correspondence(d))
        case None =>
          for {
            limit <- doc.getAsTry[Int]("limit")
            inc   <- doc.getAsTry[Int]("increment")
            byo   <- doc.getAsTry[Int]("byoyomi")
            per   <- doc.getAsTry[Int]("periods")
          } yield TimeControl.RealTime(shogi.Clock.Config(limit, inc, byo, per))
      }
    },
    {
      case TimeControl.RealTime(c) =>
        BSONDocument(
          "limit"     -> c.limitSeconds,
          "increment" -> c.incrementSeconds,
          "byoyomi"   -> c.byoyomiSeconds,
          "periods"   -> c.periodsTotal,
        )
      case TimeControl.Correspondence(d) =>
        BSONDocument("days" -> d)
    },
  )

  implicit private val spotlightBSONHandler: BSONDocumentHandler[Spotlight] =
    Macros.handler[Spotlight]

  implicit val battleBSONHandler: BSONDocumentHandler[TeamBattle] = Macros.handler[TeamBattle]

  implicit private val leaderboardRatio: BSONHandler[LeaderboardApi.Ratio] =
    BSONIntegerHandler.as[LeaderboardApi.Ratio](
      i => LeaderboardApi.Ratio(i.toDouble / 100_000),
      r => (r.value * 100_000).toInt,
    )

  import Condition.BSONHandlers.AllBSONHandler

  implicit val tournamentHandler: BSON[Tournament] = new BSON[Tournament] {
    def reads(r: BSON.Reader) = {
      val format  = r.getO[Format]("format").getOrElse(Format.Arena)
      val variant = r.intO("variant").fold[Variant](Variant.default)(Variant.orDefault)
      val position: Option[Sfen] = r.getO[Sfen]("sfen").filterNot(_.initialOf(variant))
      val startsAt               = r date "startsAt"
      val conditions             = r.getO[Condition.All]("conditions") getOrElse Condition.All.empty
      Tournament(
        id = r str "_id",
        name = r str "name",
        format = format,
        status = r.get[Status]("status"),
        timeControl = r.get[TimeControl]("clock"),
        minutes = r int "minutes",
        variant = variant,
        position = position,
        mode = r.intO("mode") flatMap Mode.apply getOrElse Mode.Rated,
        password = r.strO("password"),
        candidates = r strsD "candidates",
        conditions = conditions,
        closed = r boolD "closed",
        denied = r strsD "denied",
        teamBattle = r.getO[TeamBattle]("teamBattle"),
        candidatesOnly = r boolD "candidatesOnly",
        maxPlayers = r intO "maxPlayers",
        noBerserk = r boolD "noBerserk",
        noStreak = r boolD "noStreak",
        proMode = r boolD "proMode",
        schedule = for {
          doc   <- r.getO[Bdoc]("schedule")
          freq  <- doc.getAsOpt[Schedule.Freq]("freq")
          speed <- doc.getAsOpt[Schedule.Speed]("speed")
        } yield Schedule(format, freq, speed, variant, position, startsAt, conditions),
        nbPlayers = r int "nbPlayers",
        createdAt = r date "createdAt",
        createdBy = r strO "createdBy" getOrElse lishogiId,
        startsAt = startsAt,
        winnerId = r strO "winner",
        featuredId = r strO "featured",
        spotlight = r.getO[Spotlight]("spotlight"),
        description = r strO "description",
        hasChat = r boolO "chat" getOrElse true,
      )
    }
    def writes(w: BSON.Writer, o: Tournament) =
      $doc(
        "_id"            -> o.id,
        "name"           -> o.name,
        "format"         -> o.format.some.filterNot(_ == Format.Arena),
        "status"         -> o.status,
        "clock"          -> o.timeControl,
        "minutes"        -> o.minutes,
        "endsAt"         -> (o.hasArrangements option o.finishesAt),
        "variant"        -> o.variant.some.filterNot(_.standard).map(_.id),
        "sfen"           -> o.position.map(_.value),
        "mode"           -> o.mode.some.filterNot(_.rated).map(_.id),
        "password"       -> o.password,
        "candidates"     -> w.strListO(o.candidates),
        "conditions"     -> o.conditions.ifNonEmpty,
        "closed"         -> w.boolO(o.closed),
        "denied"         -> w.strListO(o.denied),
        "teamBattle"     -> o.teamBattle,
        "candidatesOnly" -> w.boolO(o.candidatesOnly),
        "maxPlayers"     -> o.maxPlayers.filterNot(_ == Format.maxPlayers(o.format)),
        "noBerserk"      -> w.boolO(o.noBerserk),
        "noStreak"       -> w.boolO(o.noStreak),
        "proMode"        -> w.boolO(o.proMode),
        "schedule" -> o.schedule.map { s =>
          $doc(
            "freq"  -> s.freq,
            "speed" -> s.speed,
          )
        },
        "nbPlayers"   -> o.nbPlayers,
        "createdAt"   -> w.date(o.createdAt),
        "createdBy"   -> o.nonLishogiCreatedBy,
        "startsAt"    -> w.date(o.startsAt),
        "winner"      -> o.winnerId,
        "featured"    -> o.featuredId,
        "spotlight"   -> o.spotlight,
        "description" -> o.description,
        "chat"        -> (!o.hasChat).option(false),
      )
  }

  implicit val playerBSONHandler: BSON[Player] = new BSON[Player] {
    def reads(r: BSON.Reader) =
      Player(
        _id = r str "_id",
        tourId = r str "tid",
        userId = r str "uid",
        order = r intO "o",
        rating = r int "r",
        provisional = r boolD "pr",
        withdraw = r boolD "w",
        kicked = r boolD "k",
        score = r intD "s",
        fire = r boolD "f",
        performance = r intD "e",
        team = r strO "t",
      )
    def writes(w: BSON.Writer, o: Player) =
      $doc(
        "_id" -> o._id,
        "tid" -> o.tourId,
        "uid" -> o.userId,
        "o"   -> o.order,
        "r"   -> o.rating,
        "pr"  -> w.boolO(o.provisional),
        "w"   -> w.boolO(o.withdraw),
        "k"   -> w.boolO(o.kicked),
        "s"   -> w.intO(o.score),
        "m"   -> o.magicScore,
        "f"   -> w.boolO(o.fire),
        "e"   -> o.performance,
        "t"   -> o.team,
      )
  }

  implicit val pairingHandler: BSON[Pairing] = new BSON[Pairing] {
    def reads(r: BSON.Reader) = {
      val users = r strsD "u"
      val user1 = users.headOption err "tournament pairing first user"
      val user2 = users lift 1 err "tournament pairing second user"
      Pairing(
        id = r str "_id",
        tourId = r str "tid",
        status = shogi.Status(r int "s") err "tournament pairing status",
        user1 = user1,
        user2 = user2,
        winner = r boolO "w" map {
          case true => user1
          case _    => user2
        },
        plies = r intO "t",
        berserk1 = r.intO("b1").fold(r.boolD("b1"))(1 ==), // it used to be int = 0/1
        berserk2 = r.intO("b2").fold(r.boolD("b2"))(1 ==),
      )
    }
    def writes(w: BSON.Writer, o: Pairing) =
      $doc(
        "_id" -> o.id,
        "tid" -> o.tourId,
        "s"   -> o.status.id,
        "u"   -> BSONArray(o.user1, o.user2),
        "w"   -> o.winner.map(o.user1 ==),
        "t"   -> o.plies,
        "b1"  -> w.boolO(o.berserk1),
        "b2"  -> w.boolO(o.berserk2),
      )
  }

  implicit val arrangementHandler: BSON[Arrangement] = new BSON[Arrangement] {
    def reads(r: BSON.Reader) = {
      val users   = r strsD Arrangement.BSONFields.users
      val user1Id = users.headOption err "tournament arrangement first user"
      val user2Id = users lift 1 err "tournament arrangement second user"
      Arrangement(
        id = r str Arrangement.BSONFields.id,
        tourId = r str Arrangement.BSONFields.tourId,
        user1 = Arrangement.User(
          id = user1Id,
          readyAt = r dateO Arrangement.BSONFields.u1ReadyAt,
          scheduledAt = r dateO Arrangement.BSONFields.u1ScheduledAt,
        ),
        user2 = Arrangement.User(
          id = user2Id,
          readyAt = r dateO Arrangement.BSONFields.u2ReadyAt,
          scheduledAt = r dateO Arrangement.BSONFields.u2ScheduledAt,
        ),
        name = r strO Arrangement.BSONFields.name,
        color = r.getO[shogi.Color](Arrangement.BSONFields.color),
        points = r.getO[Arrangement.Points](Arrangement.BSONFields.points),
        gameId = r strO Arrangement.BSONFields.gameId,
        startedAt = r dateO Arrangement.BSONFields.startedAt,
        status = r.intO(Arrangement.BSONFields.status) flatMap shogi.Status.apply,
        winner = r boolO Arrangement.BSONFields.winner map {
          case true => user1Id
          case _    => user2Id
        },
        plies = r intO Arrangement.BSONFields.plies,
        scheduledAt = r dateO Arrangement.BSONFields.scheduledAt,
        lockedScheduledAt = r boolD Arrangement.BSONFields.lockedScheduledAt,
        lastNotified = r dateO Arrangement.BSONFields.lastNotified,
      )
    }
    def writes(w: BSON.Writer, o: Arrangement) =
      $doc(
        Arrangement.BSONFields.id            -> o.id,
        Arrangement.BSONFields.tourId        -> o.tourId,
        Arrangement.BSONFields.users         -> BSONArray(o.user1.id, o.user2.id),
        Arrangement.BSONFields.u1ReadyAt     -> o.user1.readyAt,
        Arrangement.BSONFields.u2ReadyAt     -> o.user2.readyAt,
        Arrangement.BSONFields.u1ScheduledAt -> o.user1.scheduledAt,
        Arrangement.BSONFields.u2ScheduledAt -> o.user2.scheduledAt,
        Arrangement.BSONFields.name          -> o.name,
        Arrangement.BSONFields.color         -> o.color,
        Arrangement.BSONFields.points        -> o.points.filterNot(_ == Arrangement.Points.default),
        Arrangement.BSONFields.gameId        -> o.gameId,
        Arrangement.BSONFields.startedAt     -> o.startedAt,
        Arrangement.BSONFields.status        -> o.status.map(_.id),
        Arrangement.BSONFields.winner        -> o.winner.map(o.user1 ==),
        Arrangement.BSONFields.plies         -> o.plies,
        Arrangement.BSONFields.scheduledAt   -> o.scheduledAt,
        Arrangement.BSONFields.lockedScheduledAt -> w.boolO(o.lockedScheduledAt),
        Arrangement.BSONFields.updatedAt -> o.gameId.isEmpty ?? DateTime.now.some, // updated at
      )
  }

  implicit val leaderboardEntryHandler: BSON[LeaderboardApi.Entry] =
    new BSON[LeaderboardApi.Entry] {
      def reads(r: BSON.Reader) =
        LeaderboardApi.Entry(
          id = r str "_id",
          userId = r str "u",
          tourId = r str "t",
          nbGames = r int "g",
          score = r int "s",
          rank = r int "r",
          rankRatio = r.get[LeaderboardApi.Ratio]("w"),
          freq = r intO "f" flatMap Schedule.Freq.byId,
          speed = r intO "p" flatMap Schedule.Speed.byId,
          perf = PerfType.byId get r.int("v") err "Invalid leaderboard perf",
          date = r date "d",
        )

      def writes(w: BSON.Writer, o: LeaderboardApi.Entry) =
        $doc(
          "_id" -> o.id,
          "u"   -> o.userId,
          "t"   -> o.tourId,
          "g"   -> o.nbGames,
          "s"   -> o.score,
          "r"   -> o.rank,
          "w"   -> o.rankRatio,
          "f"   -> o.freq.map(_.id),
          "p"   -> o.speed.map(_.id),
          "v"   -> o.perf.id,
          "d"   -> w.date(o.date),
        )
    }

  import LeaderboardApi.ChartData.AggregationResult
  implicit val leaderboardAggregationResultBSONHandler: BSONDocumentHandler[AggregationResult] =
    Macros.handler[AggregationResult]
}
