package lishogi.irwin

import lishogi.report.Suspect
import lishogi.game.Game
import lishogi.analyse.Analysis

case class IrwinRequest(
    suspect: Suspect,
    origin: IrwinRequest.Origin,
    games: List[(Game, Option[Analysis])]
)

object IrwinRequest {

  sealed trait Origin {
    def key = toString.toLowerCase
  }

  object Origin {
    case object Moderator   extends Origin
    case object Tournament  extends Origin
    case object Leaderboard extends Origin
  }
}
