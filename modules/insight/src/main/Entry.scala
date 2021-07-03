package lila.insight

import shogi.opening.Ecopening
import shogi.{ Color, Role }
import lila.game.{ Game, Pov }
import lila.rating.PerfType
import org.joda.time.DateTime
import cats.data.NonEmptyList

case class Entry(
    id: String,  // gameId + w/b
    number: Int, // auto increment over userId
    userId: String,
    color: Color,
    perf: PerfType,
    eco: Option[Ecopening],
    opponentRating: Int,
    opponentStrength: RelativeStrength,
    moves: List[Move],
    bishopTrade: BishopTrade,
    rookTrade: RookTrade,
    result: Result,
    termination: Termination,
    ratingDiff: Int,
    analysed: Boolean,
    provisional: Boolean,
    date: DateTime
) {

  def gameId = id take Game.gameIdSize
}

case object Entry {

  def povToId(pov: Pov) = pov.gameId + pov.color.letter

  object BSONFields {
    val id                       = "_id"
    val number                   = "n"
    val userId                   = "u"
    val color                    = "c"
    val perf                     = "p"
    val eco                      = "e"
    val opponentRating           = "or"
    val opponentStrength         = "os"
    val moves: String            = "m"
    def moves(f: String): String = s"$moves.$f"
    val bishopTrade              = "bt"
    val rookTrade                = "rt"
    val result                   = "r"
    val termination              = "t"
    val ratingDiff               = "rd"
    val analysed                 = "a"
    val provisional              = "pr"
    val date                     = "d"
  }
}

case class Move(
    phase: Phase,
    tenths: Int,
    role: Role,
    eval: Option[Int], // before the move was played, relative to player
    mate: Option[Int], // before the move was played, relative to player
    cpl: Option[Int],  // eval diff caused by the move, relative to player, mate ~= 10
    material: Int,     // material imbalance, relative to player
    opportunism: Option[Boolean],
    luck: Option[Boolean],
    blur: Boolean,
    timeCv: Option[Float] // time coefficient variation
)

sealed abstract class Termination(val id: Int, val name: String)
object Termination {
  case object ClockFlag      extends Termination(1, "Clock flag")
  case object Disconnect     extends Termination(2, "Disconnect")
  case object Resignation    extends Termination(3, "Resignation")
  case object Draw           extends Termination(4, "Draw")
  case object Stalemate      extends Termination(5, "Stalemate")
  case object Checkmate      extends Termination(6, "Checkmate")
  case object TryRule        extends Termination(7, "Try rule")
  case object PerpetualCheck extends Termination(8, "Perpetual Check")
  case object Impasse27      extends Termination(9, "Impasse")

  val all =
    List(ClockFlag, Disconnect, Resignation, Draw, Stalemate, Checkmate, TryRule, PerpetualCheck, Impasse27)
  val byId = all map { p =>
    (p.id, p)
  } toMap

  import shogi.{ Status => S }

  def fromStatus(s: shogi.Status) =
    s match {
      case S.Timeout             => Disconnect
      case S.Outoftime           => ClockFlag
      case S.Resign              => Resignation
      case S.Draw                => Draw
      case S.Stalemate           => Stalemate
      case S.Mate | S.VariantEnd => Checkmate
      case S.TryRule             => TryRule
      case S.Impasse27           => Impasse27
      case S.PerpetualCheck      => PerpetualCheck
      case S.Cheat               => Resignation
      case S.Created | S.Started | S.Aborted | S.NoStart | S.UnknownFinish =>
        logger.error(s"Unfinished game in the insight indexer: $s")
        Resignation
    }
}

sealed abstract class Result(val id: Int, val name: String)
object Result {
  case object Win  extends Result(1, "Victory")
  case object Draw extends Result(2, "Draw")
  case object Loss extends Result(3, "Defeat")
  val all = List(Win, Draw, Loss)
  val byId = all map { p =>
    (p.id, p)
  } toMap
  val idList = all.map(_.id)
}

sealed abstract class Phase(val id: Int, val name: String)
object Phase {
  case object Opening extends Phase(1, "Opening")
  case object Middle  extends Phase(2, "Middlegame")
  case object End     extends Phase(3, "Endgame")
  val all = List(Opening, Middle, End)
  val byId = all map { p =>
    (p.id, p)
  } toMap
  def of(div: shogi.Division, ply: Int): Phase =
    div.middle.fold[Phase](Opening) {
      case m if m > ply => Opening
      case _ =>
        div.end.fold[Phase](Middle) {
          case e if e > ply => Middle
          case _            => End
        }
    }
}

sealed abstract class BishopTrade(val id: Boolean, val name: String)
object BishopTrade {
  object Yes extends BishopTrade(true, "Bishop trade")
  object No  extends BishopTrade(false, "No bishop trade")
  val all                            = List(Yes, No)
  def apply(v: Boolean): BishopTrade = if (v) Yes else No
}

sealed abstract class RookTrade(val id: Boolean, val name: String)
object RookTrade {
  object Yes extends RookTrade(true, "Rook trade")
  object No  extends RookTrade(false, "No rook trade")
  val all                          = List(Yes, No)
  def apply(v: Boolean): RookTrade = if (v) Yes else No
}

sealed abstract class RelativeStrength(val id: Int, val name: String)
object RelativeStrength {
  case object MuchWeaker   extends RelativeStrength(10, "Much weaker")
  case object Weaker       extends RelativeStrength(20, "Weaker")
  case object Similar      extends RelativeStrength(30, "Similar")
  case object Stronger     extends RelativeStrength(40, "Stronger")
  case object MuchStronger extends RelativeStrength(50, "Much stronger")
  val all = List(MuchWeaker, Weaker, Similar, Stronger, MuchStronger)
  val byId = all map { p =>
    (p.id, p)
  } toMap
  def apply(diff: Int) =
    diff match {
      case d if d < -200 => MuchWeaker
      case d if d < -100 => Weaker
      case d if d > 200  => MuchStronger
      case d if d > 100  => Stronger
      case _             => Similar
    }
}

sealed abstract class MovetimeRange(val id: Int, val name: String, val tenths: NonEmptyList[Int])
object MovetimeRange {
  case object MTR1   extends MovetimeRange(1, "0 to 1 second", NonEmptyList.of(1, 5, 10))
  case object MTR3   extends MovetimeRange(3, "1 to 3 seconds", NonEmptyList.of(15, 20, 30))
  case object MTR5   extends MovetimeRange(5, "3 to 5 seconds", NonEmptyList.of(40, 50))
  case object MTR10  extends MovetimeRange(10, "5 to 10 seconds", NonEmptyList.of(60, 80, 100))
  case object MTR30  extends MovetimeRange(30, "10 to 30 seconds", NonEmptyList.of(150, 200, 300))
  case object MTRInf extends MovetimeRange(60, "More than 30 seconds", NonEmptyList.of(400, 600))
  val all           = List(MTR1, MTR3, MTR5, MTR10, MTR30, MTRInf)
  def reversedNoInf = all.reverse drop 1
  val byId = all map { p =>
    (p.id, p)
  } toMap
}

sealed abstract class MaterialRange(val id: Int, val name: String, val imbalance: Int) {
  def negative = imbalance <= 0
}
object MaterialRange {
  case object Down4 extends MaterialRange(1, "Less than -6", -6)
  case object Down3 extends MaterialRange(2, "-3 to -6", -3)
  case object Down2 extends MaterialRange(3, "-1 to -3", -1)
  case object Down1 extends MaterialRange(4, "0 to -1", 0)
  case object Equal extends MaterialRange(5, "Equal", 0)
  case object Up1   extends MaterialRange(6, "0 to +1", 1)
  case object Up2   extends MaterialRange(7, "+1 to +3", 3)
  case object Up3   extends MaterialRange(8, "+3 to +6", 6)
  case object Up4   extends MaterialRange(9, "More than +6", Int.MaxValue)
  val all                     = List(Down4, Down3, Down2, Down1, Equal, Up1, Up2, Up3, Up4)
  def reversedButEqualAndLast = all.diff(List(Equal, Up4)).reverse
  val byId = all map { p =>
    (p.id, p)
  } toMap
}

sealed abstract class Blur(val id: Boolean, val name: String)
object Blur {
  object Yes extends Blur(true, "Blur")
  object No  extends Blur(false, "No blur")
  val all                     = List(Yes, No)
  def apply(v: Boolean): Blur = if (v) Yes else No
}

sealed abstract class TimeVariance(val id: Float, val name: String) {
  lazy val intFactored = (id * TimeVariance.intFactor).toInt
}
object TimeVariance {
  case object VeryConsistent  extends TimeVariance(0.25f, "Very consistent")
  case object QuiteConsistent extends TimeVariance(0.4f, "Quite consistent")
  case object Medium          extends TimeVariance(0.6f, "Medium")
  case object QuiteVariable   extends TimeVariance(0.75f, "Quite variable")
  case object VeryVariable    extends TimeVariance(1f, "Very variable")
  val all = List(VeryConsistent, QuiteConsistent, Medium, QuiteVariable, VeryVariable)
  val byId = all map { p =>
    (p.id, p)
  } toMap
  def apply(v: Float) = all.find(_.id >= v) | VeryVariable
  val intFactor: Int  = 100_000 // multiply variance by that to get an Int for storage
}
