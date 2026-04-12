package lila.game

import play.api.libs.json._

import shogi.Centis
import shogi.Color
import shogi.Situation
import shogi.Status
import shogi.format.forsyth.Sfen
import shogi.format.usi.Usi
import shogi.{ Clock => ShogiClock }

import lila.chat.AnonLine
import lila.chat.UserLine
import lila.common.Json._
import lila.game.JsonView._

sealed trait Event {
  def typ: String
  def data: JsValue
  def only: Option[Color]   = None
  def owner: Boolean        = false
  def troll: Boolean        = false
  def moveBy: Option[Color] = None
}

object Event {

  sealed trait Empty extends Event {
    def data = JsNull
  }

  object Start extends Empty {
    def typ = "start"
  }

  case class UsiEvent(
      usi: Usi,
      sfen: Sfen,
      check: Boolean,
      state: State,
      clock: Option[ClockEvent],
  ) extends Event {
    def typ = "usi"
    def data = Json
      .obj(
        "usi"  -> usi.usi,
        "sfen" -> sfen,
        "ply"  -> state.plies,
      )
      .add("clock" -> clock.map(_.data))
      .add("status" -> state.status)
      .add("winner" -> state.winner)
      .add("check" -> check)

    override def moveBy = Some(!state.color)
  }

  object UsiEvent {
    def apply(
        usi: Usi,
        situation: Situation,
        state: State,
        clock: Option[ClockEvent],
    ): UsiEvent =
      UsiEvent(
        usi = usi,
        sfen = situation.toSfen,
        check = situation.check,
        state = state,
        clock = clock,
      )
  }

  case class RedirectOwner(
      color: Color,
      id: String,
      cookie: Option[JsObject],
  ) extends Event {
    def typ = "redirect"
    def data =
      Json
        .obj(
          "id"  -> id,
          "url" -> s"/$id",
        )
        .add("cookie" -> cookie)
    override def only = Some(color)
  }

  case class AnonMessage(line: AnonLine) extends Event {
    def typ            = "message"
    def data           = lila.chat.JsonView(line)
    override def troll = false
  }

  case class UserMessage(line: UserLine) extends Event {
    def typ            = "message"
    def data           = lila.chat.JsonView(line)
    override def troll = line.troll
  }

  case class EndData(game: Game, afterPerfsData: Option[(RatingDiffs, Provisionals)])
      extends Event {
    def typ = "endData"
    def data =
      Json
        .obj(
          "winner" -> game.winnerColor,
          "status" -> game.status,
        )
        .add("clock" -> game.clock.map { c =>
          val senteClock = c currentClockFor Color.Sente
          val goteClock  = c currentClockFor Color.Gote
          Json.obj(
            "sc" -> senteClock.time.centis,
            "gc" -> goteClock.time.centis,
            "sp" -> senteClock.periods,
            "gp" -> goteClock.periods,
          )
        })
        .add("ratingDiff" -> afterPerfsData.map { case (rds, _) =>
          Json.obj(
            Color.Sente.name -> rds.sente,
            Color.Gote.name  -> rds.gote,
          )
        })
        .add("provisionals" -> afterPerfsData.map { case (_, ps) =>
          Json.obj(
            Color.Sente.name -> ps.sente,
            Color.Gote.name  -> ps.gote,
          )
        })
        .add("boosted" -> game.boosted)
        .add("illegalUsi" -> game.illegalUsi.map(_.usi))
  }

  case object Reload extends Empty {
    def typ = "reload"
  }
  case object ReloadOwner extends Empty {
    def typ            = "reload"
    override def owner = true
  }

  case class RematchOffer(by: Option[Color]) extends Event {
    def typ  = "rematchOffer"
    def data = Json.toJson(by)
    override def owner = true
  }

  case class RematchTaken(nextId: Game.ID) extends Event {
    def typ  = "rematchTaken"
    def data = Json.toJson(nextId)
  }

  case class DrawOffer(by: Option[Color]) extends Event {
    def typ            = "drawOffer"
    def data           = Json.toJson(by)
    override def owner = true
  }

  case class ClockInc(color: Color, time: Centis) extends Event {
    def typ = "clockInc"
    def data =
      Json.obj(
        "color" -> color,
        "time"  -> time.centis,
      )
  }

  sealed trait ClockEvent extends Event

  case class Clock(
      sente: Centis,
      gote: Centis,
      sPer: Int = 0,
      gPer: Int = 0,
      nextLagComp: Option[Centis] = None,
  ) extends ClockEvent {
    def typ = "clock"
    def data =
      Json
        .obj(
          "sente" -> sente.toSeconds,
          "gote"  -> gote.toSeconds,
          "sPer"  -> sPer,
          "gPer"  -> gPer,
        )
        .add("lag" -> nextLagComp.collect { case Centis(c) if c > 1 => c })
  }
  object Clock {
    def apply(clock: ShogiClock): Clock = {
      val senteClock = clock currentClockFor Color.Sente
      val goteClock  = clock currentClockFor Color.Gote
      Clock(
        sente = senteClock.time,
        gote = goteClock.time,
        sPer = senteClock.periods,
        gPer = goteClock.periods,
        nextLagComp = clock lagCompEstimate clock.color,
      )
    }
  }

  case class Berserk(color: Color) extends Event {
    def typ  = "berserk"
    def data = Json.toJson(color)
  }

  case class CorrespondenceClock(sente: Float, gote: Float) extends ClockEvent {
    def typ  = "cclock"
    def data = Json.obj("sente" -> sente, "gote" -> gote)
  }
  object CorrespondenceClock {
    def apply(clock: lila.game.CorrespondenceClock): CorrespondenceClock =
      CorrespondenceClock(clock.senteTime, clock.goteTime)
  }

  case class State(
      color: Color,
      plies: Int,
      status: Option[Status],
      winner: Option[Color],
  ) extends Event {
    def typ = "state"
    def data =
      Json
        .obj(
          "color" -> color,
          "plies" -> plies,
        )
        .add("status" -> status)
        .add("winner" -> winner)
  }

  case class TakebackOffers(
      sente: Boolean,
      gote: Boolean,
  ) extends Event {
    def typ = "takebackOffers"
    def data =
      Json
        .obj()
        .add("sente" -> sente)
        .add("gote" -> gote)
    override def owner = true
  }

  case class PauseOffer(by: Option[Color]) extends Event {
    def typ            = "pauseOffer"
    def data           = Json.toJson(by)
    override def owner = true
  }

  case class ResumeOffer(by: Option[Color]) extends Event {
    def typ            = "resumeOffer"
    def data           = Json.toJson(by)
    override def owner = true
  }

}
