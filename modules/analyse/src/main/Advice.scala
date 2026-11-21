package lila.analyse

import scala.util.chaining._

import shogi.format.Glyph

import lila.i18n.{ I18nKeys => trans }
import lila.tree.Eval._

sealed trait Advice {
  def judgment: Advice.Judgement
  def info: Info
  def prev: Info

  def ply   = info.ply
  def turn  = info.turn
  def color = info.color
  def cp    = info.cp
  def mate  = info.mate

  def makeComment(withEval: Boolean, withBestMove: Boolean): String =
    withEval.??(evalComment ?? { c =>
      s"($c) "
    }) +
      (this match {
        case Advice.MateAdvice(seq, _, _, _) => i18n(seq.descKey)
        case Advice.CpAdvice(judgment, _, _) => i18n(judgment.key)
      }) + s"${i18n(trans.dotSymbol.key)} " + {
        withBestMove ?? {
          info.variation.headOption ?? { usi =>
            i18n(trans.bestMoveWasX.key, lila.common.String.usiNotationFormat(ply - 1, usi))
          }
        }
      }

  def evalComment: Option[String] = {
    List(prev.evalComment, info.evalComment).flatten mkString " → "
  }.some filter (_.nonEmpty)

  private def i18n(key: String, args: String*): String =
    s"i18n{$key;${args.mkString(",")}}"

}

object Advice {

  sealed abstract class Judgement(val glyph: Glyph, val key: String) {
    override def toString = key
  }
  object Judgement {
    object Inaccuracy extends Judgement(Glyph.MoveOrDropAssessment.dubious, trans.inaccuracy.key)
    object Mistake    extends Judgement(Glyph.MoveOrDropAssessment.mistake, trans.mistake.key)
    object Blunder    extends Judgement(Glyph.MoveOrDropAssessment.blunder, trans.blunder.key)

    val all = List(Inaccuracy, Mistake, Blunder)
  }

  def apply(prev: Info, info: Info): Option[Advice] =
    CpAdvice(prev, info) orElse MateAdvice(prev, info)

  private[analyse] case class CpAdvice(
      judgment: Advice.Judgement,
      info: Info,
      prev: Info,
  ) extends Advice

  private[analyse] object CpAdvice {

    private def cpWinningChances(cp: Double): Double = 2 / (1 + Math.exp(-0.0007 * cp)) - 1

    private val winningChanceJudgments = List(
      .3 -> Judgement.Blunder,
      .2 -> Judgement.Mistake,
      .1 -> Judgement.Inaccuracy,
    )

    def apply(prev: Info, info: Info): Option[CpAdvice] =
      for {
        cp     <- prev.cp map (_.ceiled.centipawns)
        infoCp <- info.cp map (_.ceiled.centipawns)
        prevWinningChances    = cpWinningChances(cp)
        currentWinningChances = cpWinningChances(infoCp)
        delta = (currentWinningChances - prevWinningChances) pipe { d =>
          info.color.fold(-d, d)
        }
        judgment <- winningChanceJudgments find { case (d, _) => d <= delta } map (_._2)
      } yield CpAdvice(judgment, info, prev)
  }

  sealed trait MateSequence {
    def descKey: String
  }
  object MateSequence {
    case object MateCreated extends MateSequence {
      val descKey = trans.mateCreatedDescription.key
    }
    case object MateDelayed extends MateSequence {
      val descKey = trans.mateDelayedDescription.key
    }
    case object MateLost extends MateSequence {
      val descKey = trans.mateLostDescription.key
    }

    def apply(prev: Option[Mate], next: Option[Mate]): Option[MateSequence] =
      (prev, next).some collect {
        case (None, Some(n)) if n.negative                  => MateSequence.MateCreated
        case (Some(p), None) if p.positive                  => MateSequence.MateLost
        case (Some(p), Some(n)) if p.positive && n.negative => MateSequence.MateLost
      }
  }

  private[analyse] case class MateAdvice(
      sequence: MateSequence,
      judgment: Judgement,
      info: Info,
      prev: Info,
  ) extends Advice
  private[analyse] object MateAdvice {

    def apply(prev: Info, info: Info): Option[MateAdvice] = {
      def invertCp(cp: Cp)       = cp invertIf info.color.gote
      def invertMate(mate: Mate) = mate invertIf info.color.gote
      def prevCp                 = prev.cp.map(invertCp).??(_.centipawns)
      def nextCp                 = info.cp.map(invertCp).??(_.centipawns)
      MateSequence(prev.mate map invertMate, info.mate map invertMate) flatMap { sequence =>
        import Judgement._
        import MateSequence._
        val judgment: Option[Judgement] = sequence match {
          case MateCreated if prevCp < -4000 => Option(Inaccuracy)
          case MateCreated if prevCp < -1000 => Option(Mistake)
          case MateCreated                   => Option(Blunder)
          case MateLost if nextCp > 4000     => Option(Inaccuracy)
          case MateLost if nextCp > 1000     => Option(Mistake)
          case MateLost                      => Option(Blunder)
          case MateDelayed                   => None
        }
        judgment map { MateAdvice(sequence, _, info, prev) }
      }
    }
  }

}
