package lila.round

import shogi.Color

import lila.game.Event
import lila.game.Game
import lila.game.Pov
import lila.game.Progress
import lila.pref.Pref
import lila.pref.PrefApi

final private class Moretimer(
    messenger: Messenger,
    prefApi: PrefApi,
    duration: MoretimeDuration,
)(implicit ec: scala.concurrent.ExecutionContext) {

  // pov of the player giving more time
  def apply(pov: Pov): Fu[Option[Progress]] =
    IfAllowed(pov.game) {
      (pov.game moretimeable !pov.color) ?? {
        if (pov.game.hasClock) give(pov.game, List(!pov.color), duration).some
        else
          pov.game.hasCorrespondenceClock option {
            messenger.volatile(pov.game, s"${!pov.color} gets more time")
            val p = pov.game.correspondenceGiveTime
            p.game.correspondenceClock.map(Event.CorrespondenceClock.apply).fold(p)(p + _)
          }
      }
    }

  def isAllowedIn(game: Game): Fu[Boolean] =
    if (game.isMandatory) fuFalse
    else isAllowedByPrefs(game)

  private[round] def give(game: Game, colors: List[Color], duration: MoretimeDuration): Progress =
    game.clock.fold(Progress(game)) { clock =>
      val centis = duration.value.toCentis
      val newClock = colors.foldLeft(clock) { case (c, color) =>
        c.giveTime(color, centis)
      }
      colors.foreach { c =>
        messenger.volatile(game, s"$c + ${duration.value.toSeconds} seconds")
      }
      (game withClock newClock) ++ colors.map { Event.ClockInc(_, centis) }
    }

  private def isAllowedByPrefs(game: Game): Fu[Boolean] =
    game.userIds.map {
      prefApi.getPref(_, (p: Pref) => p.moretime)
    }.sequenceFu dmap {
      _.forall { p =>
        p == Pref.Takeback.ALWAYS || (p == Pref.Takeback.CASUAL && game.casual)
      }
    }

  private def IfAllowed[A](game: Game)(f: => A): Fu[A] =
    if (!game.playable) fufail(ClientError("[moretimer] game is over or paused " + game.id))
    else if (game.isMandatory) fufail(ClientError("[moretimer] game disallows it " + game.id))
    else
      isAllowedByPrefs(game) flatMap {
        case true => fuccess(f)
        case _    => fufail(ClientError("[moretimer] disallowed by preferences " + game.id))
      }
}
