package shogi
package format
package kif

import play.api.libs.json._
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

import scala._

case class Kifu(
    tags: Tags,
    moves: List[Move],
    initial: Initial = Initial.empty
) {

  def updatePly(ply: Int, f: Move => Move) = {
    val index = ply - 1
    (moves lift index).fold(this) { move =>
      copy(moves = moves.updated(index, f(move)))
    }
  }
  def updateLastPly(f: Move => Move) = updatePly(nbPlies, f)

  def nbPlies = moves.size

  def withEvent(title: String) =
    copy(
      tags = tags + Tag(_.Event, title)
    )

  def render: String = {
    val initStr =
      if (initial.comments.nonEmpty) initial.comments.mkString("{ ", " } { ", " }\n")
      else ""
    // TODO: Zip with ply (move) numbers
    val turnStr = moves mkString " "
    val endStr  = tags(_.Result) | ""
    s"$tags\n\n$initStr$turnStr $endStr"
  }.trim

  def renderAsKifu(uciKifu: scala.collection.IndexedSeq[(String, String)], gameCreatedAt: DateTime) = {
    val fmt            = DateTimeFormat.forPattern("yyyy/MM/dd HH:mm:ss")
    val gameCreatedTag = "開始日時：" + fmt.print(gameCreatedAt) + "\n"
    val tagsStr        = KifuUtils tagsAsKifu tags mkString "\n"
    val movesHeader    = """
手数----指手---------消費時間--
"""
    val uciKifuAsVector = uciKifu.foldLeft(Vector[(String, String)]()) { _ :+ _ }
    val movesVector    = KifuUtils.movesAsKifu(uciKifuAsVector)
    val movesStr       = movesVector.zipWithIndex map { move => s"${move._2 + 1} ${move._1}" } mkString "\n"

    val endMoveStr = ""
    s"$gameCreatedTag$tagsStr$movesHeader$movesStr\n$endMoveStr"
  }

  override def toString = render
}

case class Initial(comments: List[String] = Nil)

object Initial {
  val empty = Initial(Nil)
}

case class Move(
    // TODO: remove ply (single responsibility principle)
    ply: Int,
    san: String,
    comments: List[String] = Nil,
    glyphs: Glyphs = Glyphs.empty,
    opening: Option[String] = None,
    result: Option[String] = None,
    variations: List[List[Move]] = Nil,
    // time left for the user who made the move, after he made it
    secondsLeft: Option[Int] = None
) {

  def isLong = comments.nonEmpty || variations.nonEmpty

  private def clockString: Option[String] =
    secondsLeft.map(seconds => "[%clk " + Move.formatKifuSeconds(seconds) + "]")

  override def toString = {
    val glyphStr = glyphs.toList
      .map({
        case glyph if glyph.id <= 6 => glyph.symbol
        case glyph                  => s" $$${glyph.id}"
      })
      .mkString
    val commentsOrTime =
      if (comments.nonEmpty || secondsLeft.isDefined || opening.isDefined || result.isDefined)
        List(clockString, opening, result).flatten
          .:::(comments map Move.noDoubleLineBreak)
          .map { text =>
            s" { $text }"
          }
          .mkString
      else ""
    val variationString =
      if (variations.isEmpty) ""
      else variations.map(_.mkString(" (", " ", ")")).mkString(" ")
    s"$ply. $san$glyphStr$commentsOrTime$variationString"
  }
}

object Move {

  private val noDoubleLineBreakRegex = "(\r?\n){2,}".r

  private def noDoubleLineBreak(txt: String) =
    noDoubleLineBreakRegex.replaceAllIn(txt, "\n")

  private def formatKifuSeconds(t: Int) =
    periodFormatter.print(
      org.joda.time.Duration.standardSeconds(t).toPeriod
    )

  private[this] val periodFormatter = new org.joda.time.format.PeriodFormatterBuilder().printZeroAlways
    .minimumPrintedDigits(1)
    .appendHours
    .appendSeparator(":")
    .minimumPrintedDigits(2)
    .appendMinutes
    .appendSeparator(":")
    .appendSeconds
    .toFormatter

}
