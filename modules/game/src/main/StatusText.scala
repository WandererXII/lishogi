package lila.game

import shogi.variant.Variant
import shogi.{ Color, Status }

object StatusText {

  import Status._

  def apply(status: Status, win: Option[Color], variant: Variant): String =
    status match {
      case Aborted                  => "Game was aborted."
      case Mate                     => s"${winner(win)} wins by checkmate."
      case Resign                   => s"${loser(win)} resigns."
      case UnknownFinish            => s"${winner(win)} wins."
      case Stalemate                => s"${winner(win)} wins by stalemate."
      case TryRule                  => s"${winner(win)} wins by try rule."
      case Impasse27                => s"${winner(win)} wins by impasse."
      case PerpetualCheck           => s"${loser(win)} lost due to perpetual check."
      case RoyalsLost               => s"${winner(win)} wins by capturing all royal pieces."
      case BareKing                 => s"${winner(win)} wins due to bare king rule."
      case Timeout if win.isDefined => s"${loser(win)} left the game."
      case Timeout | Draw           => "The game is a draw."
      case Outoftime                => s"${winner(win)} wins on time."
      case NoStart                  => s"${loser(win)} wins by forfeit."
      case Cheat                    => "Cheat detected."
      case _                        => ""
    }

  def apply(game: lila.game.Game): String = apply(game.status, game.winnerColor, game.variant)

  private def winner(win: Option[Color]) = win.??(_.toString)
  private def loser(win: Option[Color])  = winner(win.map(!_))
}
