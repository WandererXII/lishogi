package lila.analyse

import shogi.format.kif.{ Glyphs, Kifu, Move, Tag }
import shogi.opening._
import shogi.{ Color, Status }

final class Annotator(netDomain: lila.common.config.NetDomain) {

  def apply(
      p: Kifu,
      analysis: Option[Analysis],
      opening: Option[FullOpening.AtPly],
      winner: Option[Color],
      status: Status
  ): Kifu =
    annotateStatus(winner, status) {
      annotateOpening(opening) {
        annotateMoves(p, analysis ?? (_.advices))
      }.copy(
        tags = p.tags + Tag(_.Annotator, netDomain)
      )
    }

  private def annotateStatus(winner: Option[Color], status: Status)(p: Kifu) =
    lila.game.StatusText(status, winner, shogi.variant.Standard) match {
      case ""   => p
      case text => p.updateLastPly(_.copy(result = text.some))
    }

  private def annotateOpening(opening: Option[FullOpening.AtPly])(p: Kifu) =
    opening.fold(p) { o =>
      p.updatePly(o.ply, _.copy(opening = o.opening.ecoName.some))
    }

  private def annotateMoves(p: Kifu, advices: List[Advice]): Kifu =
    advices.foldLeft(p) { case (pgn, advice) =>
      pgn.updatePly(
        advice.ply,
        move =>
          move.copy(
            glyphs = Glyphs.fromList(advice.judgment.glyph :: Nil),
            comments = advice.makeComment(true, true) :: move.comments,
            variations = makeVariation(advice.ply, advice) :: Nil
          )
      )
    }

  private def makeVariation(ply: Int, advice: Advice): List[Move] =
    (advice.info.variation take 20).zipWithIndex.map { case (san, index) =>
      Move(ply + index, san)
    }
}
