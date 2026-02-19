package lila.round

import cats.data.Validated
import cats.data.Validated.Invalid
import cats.data.Validated.Valid

import shogi.Centis
import shogi.LagMetrics
import shogi.Status
import shogi.format.usi.Usi

import lila.common.Bus
import lila.game.Game
import lila.game.Game.PlayerId
import lila.game.GameRepo
import lila.game.Pov
import lila.game.Progress
import lila.game.actorApi.MoveGameEvent
import lila.game.actorApi.PauseGame
import lila.hub.actorApi.round.ShoginetResign
import lila.round.actorApi.round.DrawNo
import lila.round.actorApi.round.ForecastPlay
import lila.round.actorApi.round.HumanPlay
import lila.round.actorApi.round.PauseNo
import lila.round.actorApi.round.TakebackNo
import lila.round.actorApi.round.TooManyPlies

final private class Player(
    shoginetPlayer: lila.shoginet.Player,
    gameRepo: GameRepo,
    finisher: Finisher,
    scheduleExpiration: ScheduleExpiration,
)(implicit ec: scala.concurrent.ExecutionContext) {

  sealed private trait UsiResult
  private case object Flagged                         extends UsiResult
  private case class Illegal(game: Game, err: String) extends UsiResult
  private case class UsiApplied(progress: Progress)   extends UsiResult

  private[round] def human(play: HumanPlay, round: RoundDuct)(
      pov: Pov,
  )(implicit proxy: GameProxy): Fu[Events] =
    play match {
      case HumanPlay(_, usi, ply, blur, lag, _) =>
        pov match {
          case Pov(game, _) if game.playedPlies > Game.maxPlies(game.variant) =>
            round ! TooManyPlies
            fuccess(Nil)
          case Pov(game, color) if game.playableBy(color) && game.plies == ply =>
            applyUsi(game, usi, blur, lag)
              .leftMap(e => s"$pov $e")
              .fold(errs => fufail(ClientError(errs.toString)), fuccess)
              .flatMap {
                case Flagged       => finisher.outOfTime(game)
                case Illegal(g, _) => finisher.illegal(g)
                case UsiApplied(progress) =>
                  proxy.save(progress) >>
                    postHumanOrBotPlay(round, pov, progress, usi)
              }
          case Pov(game, _) if game.finished =>
            fufail(ClientError(s"$pov game is finished ($ply. $usi)"))
          case Pov(game, _) if game.paused =>
            fufail(ClientError(s"$pov game is paused ($ply. $usi)"))
          case Pov(game, _) if game.aborted =>
            fufail(ClientError(s"$pov game is aborted ($ply. $usi)"))
          case Pov(game, color) if !game.turnOf(color) =>
            fufail(ClientError(s"$pov not your turn ($ply. $usi)"))
          case Pov(game, _) if game.plies != ply =>
            fufail(ClientError(s"$pov send wrong ply  ($ply. $usi, server: ${game.plies})"))
          case _ => fufail(ClientError(s"$pov move refused for some reason ($ply. $usi)"))
        }
    }

  private[round] def bot(usi: Usi, round: RoundDuct)(
      pov: Pov,
  )(implicit proxy: GameProxy): Fu[Events] =
    pov match {
      case Pov(game, _) if game.playedPlies > Game.maxPlies(game.variant) =>
        round ! TooManyPlies
        fuccess(Nil)
      case Pov(game, color) if game playableBy color =>
        applyUsi(game, usi, blur = false, botLag)
          .fold(errs => fufail(ClientError(errs.toString)), fuccess)
          .flatMap {
            case Flagged         => finisher.outOfTime(game)
            case Illegal(_, err) => fufail(ClientError(err.toString))
            case UsiApplied(progress) =>
              proxy.save(progress) >> postHumanOrBotPlay(round, pov, progress, usi)
          }
      case Pov(game, _) if game.finished => fufail(ClientError(s"$pov game is finished ($usi)"))
      case Pov(game, _) if game.aborted  => fufail(ClientError(s"$pov game is aborted ($usi)"))
      case Pov(game, color) if !game.turnOf(color) =>
        fufail(ClientError(s"$pov not your turn ($usi)"))
      case _ => fufail(ClientError(s"$pov move refused for some reason ($usi)"))
    }

  private def postHumanOrBotPlay(
      round: RoundDuct,
      pov: Pov,
      progress: Progress,
      usi: Usi,
  )(implicit proxy: GameProxy): Fu[Events] = {
    if (progress.game.paused) notifyOfPausedGame(usi, progress.game)
    else notifyUsi(usi, progress.game)

    if (progress.game.finished) usiFinish(progress.game) dmap { progress.events ::: _ }
    else {
      if (progress.game.playableByAi) requestShoginet(progress.game, round)
      if (pov.opponent.isOfferingDraw) round ! DrawNo(PlayerId(pov.player.id))
      if (pov.player.isProposingTakeback) round ! TakebackNo(PlayerId(pov.player.id))
      if (pov.player.isOfferingPause) round ! PauseNo(PlayerId(pov.player.id))
      if (progress.game.forecastable) round ! ForecastPlay(usi)
      scheduleExpiration(progress.game)
      fuccess(progress.events)
    }
  }

  private[round] def shoginet(game: Game, ply: Int, usi: Usi)(implicit
      proxy: GameProxy,
  ): Fu[Events] = {
    if (game.playable && game.player.isAi && game.plies == ply) {
      applyUsi(game, usi, blur = false, metrics = shoginetLag)
        .fold(errs => fufail(ClientError(errs.toString)), fuccess)
        .flatMap {
          case Flagged         => finisher.outOfTime(game)
          case Illegal(_, err) => fufail(ClientError(err.toString))
          case UsiApplied(progress) =>
            proxy.save(progress) >>-
              notifyUsi(usi, progress.game) >> {
                if (progress.game.finished) usiFinish(progress.game) dmap { progress.events ::: _ }
                else
                  fuccess(progress.events)
              }
        }
    } else
      fufail(
        ShoginetError(
          s"Not AI turn move: ${usi} id: ${game.id} playable: ${game.playable} player: ${game.player} plies: ${game.playedPlies}, $ply",
        ),
      )
  }

  private[round] def requestShoginet(game: Game, round: RoundDuct): Funit =
    game.playableByAi ?? {
      if (game.playedPlies <= shoginetPlayer.maxPlies) shoginetPlayer(game)
      else fuccess(round ! ShoginetResign)
    }

  private val shoginetLag = LagMetrics(clientLag = Centis(5).some)
  private val botLag      = LagMetrics(clientLag = Centis(10).some)

  private def applyUsi(
      game: Game,
      usi: Usi,
      blur: Boolean,
      metrics: LagMetrics,
  ): Validated[String, UsiResult] =
    game.shogi(usi, metrics) match {
      case Valid(nsg) =>
        Valid(
          if (nsg.clock.exists(_.outOfTime(game.turnColor, withGrace = false))) Flagged
          else if (game.prePaused && !nsg.situation.end)
            UsiApplied(game.pauseAndSealUsi(usi, nsg, blur))
          else UsiApplied(game.applyGame(nsg, blur)),
        )
      case Invalid(err) if (game.isProMode && illegalSanityCheck(game.shogi, usi)) =>
        Valid(Illegal(game.withIllegalUsi(usi), err))
      case i @ Invalid(_) => i
    }

  private def illegalSanityCheck(shogiGame: shogi.Game, usi: Usi): Boolean =
    usi match {
      case u: Usi.Move => shogiGame.board(u.orig).exists(_.color == shogiGame.color)
      case u: Usi.Drop =>
        shogiGame.board(u.pos).isEmpty && shogiGame.hands(shogiGame.color)(u.role) > 0
    }

  private def notifyOfPausedGame(usi: Usi, game: Game): Funit = {
    gameRepo.pause(game.id, usi).void >>- Bus.publish(PauseGame(game), "pauseGame")
  }

  private[round] def notifyUsi(usi: Usi, game: Game): Unit = {
    import lila.hub.actorApi.round.{ CorresMoveEvent, MoveEvent, SimulMoveEvent }
    val color = !game.situation.color
    val moveEvent = MoveEvent(
      gameId = game.id,
      sfen = game.situation.toSfen.value,
      usi = usi.usi,
    )

    // I checked and the bus doesn't do much if there's no subscriber for a classifier,
    // so we should be good here.
    // also used for targeted TvBroadcast subscription
    Bus.publish(MoveGameEvent(game, moveEvent.sfen, moveEvent.usi), MoveGameEvent makeChan game.id)

    // publish correspondence moves
    if (game.isCorrespondence && game.nonAi)
      Bus.publish(
        CorresMoveEvent(
          move = moveEvent,
          playerUserId = game.player(color).userId,
          mobilePushable = game.mobilePushable,
          alarmable = game.alarmable,
          unlimited = game.isUnlimited,
        ),
        "moveEventCorres",
      )

    // publish simul moves
    for {
      simulId        <- game.simulId
      opponentUserId <- game.player(!color).userId
    } Bus.publish(
      SimulMoveEvent(move = moveEvent, simulId = simulId, opponentUserId = opponentUserId),
      "moveEventSimul",
    )
  }

  private def usiFinish(game: Game)(implicit proxy: GameProxy): Fu[Events] = {
    game.status match {
      case Status.Mate       => finisher.other(game, _.Mate, winner = game.situation.winner)
      case Status.Stalemate  => finisher.other(game, _.Stalemate, winner = game.situation.winner)
      case Status.Impasse27  => finisher.other(game, _.Impasse27, winner = game.situation.winner)
      case Status.Repetition => finisher.other(game, _.Repetition, winner = game.situation.winner)
      case Status.PerpetualCheck =>
        finisher.other(game, _.PerpetualCheck, winner = game.situation.winner)
      case Status.RoyalsLost => finisher.other(game, _.RoyalsLost, winner = game.situation.winner)
      case Status.BareKing   => finisher.other(game, _.BareKing, winner = game.situation.winner)
      case Status.TryRule    => finisher.other(game, _.TryRule, winner = game.situation.winner)
      case Status.Check =>
        finisher.other(game, _.Check, winner = game.situation.winner)
      case Status.Draw => finisher.other(game, _.Draw, winner = none)
      case _           => fuccess(Nil)
    }
  }
}
