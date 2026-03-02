package lila.challenge

import scala.util.chaining._

import shogi.Color
import shogi.Mode

import lila.game.Game
import lila.game.Player
import lila.game.Pov
import lila.game.Source
import lila.user.User

final private class Joiner(
    gameRepo: lila.game.GameRepo,
    userRepo: lila.user.UserRepo,
    onStart: lila.round.OnStart,
)(implicit ec: scala.concurrent.ExecutionContext) {

  def apply(c: Challenge, destUser: Option[User], color: Option[Color]): Fu[Option[Pov]] =
    gameRepo exists c.id flatMap {
      case true                                                           => fuccess(None)
      case _ if color.map(Challenge.ColorChoice.apply).has(c.colorChoice) => fuccess(None)
      case _ if !c.hasClock && destUser.isEmpty                           => fuccess(None)
      case _ =>
        c.challengerUserId.??(userRepo.byId) flatMap { challengerUser =>
          val shogiGame =
            shogi
              .Game(c.initialSfen, c.variant)
              .withClock(c.clock.map(_.config.toClock))
          val game = Game
            .make(
              shogi = shogiGame,
              initialSfen = c.initialSfen,
              sentePlayer =
                Player.make(shogi.Sente, c.finalColor.fold(challengerUser, destUser), c.perfType),
              gotePlayer =
                Player.make(shogi.Gote, c.finalColor.fold(destUser, challengerUser), c.perfType),
              mode = if (c.initialSfen.isDefined) Mode.Casual else c.mode,
              proMode = ~c.proMode,
              source = if (c.tourInfo.isDefined) Source.Tournament else Source.Friend,
              daysPerTurn = c.daysPerTurn,
              notationImport = None,
            )
            .withId(c.id)
            .pipe { g =>
              c.tourInfo
                .fold(g) { t =>
                  g.withTournamentId(t.tournamentId).withArrangementId(t.arrangementId)
                }
            }
            .start
          (gameRepo insertDenormalized game) >>- onStart(game.id) inject Pov(
            game,
            !c.finalColor,
          ).some
        }
    }

}
