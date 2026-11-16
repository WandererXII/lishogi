package lila.importer

import scala.util.chaining._

import play.api.data.Forms._
import play.api.data._

import cats.data.Validated

import shogi.Color
import shogi.Mode
import shogi.Position
import shogi.Replay
import shogi.Status
import shogi.format.ParsedNotation
import shogi.format.Tag
import shogi.format.TagType
import shogi.format.csa.CsaParser
import shogi.format.kif.KifParser

import lila.game._

final class DataForm {

  lazy val importForm = Form(
    mapping(
      "notation" -> nonEmptyText.verifying("invalidNotation", p => checkNotation(p).isValid),
      "analyse"  -> optional(nonEmptyText),
    )(ImportData.apply)(ImportData.unapply),
  )

  def checkNotation(notation: String): Validated[String, Preprocessed] =
    ImportData(notation, none).preprocess(none)
}

private case class TagResult(status: Status, winner: Option[Color])
case class Preprocessed(
    game: NewGame,
    setup: Position,
    parsed: ParsedNotation,
)

case class ImportData(notation: String, analyse: Option[String]) {

  private type TagPicker = Tag.type => TagType

  private val maxPlies  = 600
  private val maxLength = 32768

  val notationTrunc = notation take (maxLength * 2)

  // csa and kif together
  private def createStatus(termination: String): Status =
    termination match {
      case "" | "投了" | "TORYO"                      => Status.Resign
      case "詰み" | "TSUMI"                           => Status.Mate
      case "中断" | "CHUDAN"                          => Status.Aborted
      case "持将棋" | "千日手" | "JISHOGI" | "SENNICHITE" => Status.Repetition
      case "引き分け" | "引分け" | "HIKIWAKE"              => Status.Draw
      case "入玉勝ち" | "KACHI"                         => Status.Impasse27
      case "切れ負け" | "TIME-UP" | "TIME_UP"           => Status.Outoftime
      case "反則勝ち" | "反則負け" | "ILLEGAL_MOVE" | "+ILLEGAL_ACTION" | "-ILLEGAL_ACTION" =>
        Status.UnknownFinish
      case _ => Status.UnknownFinish
    }

  private val isCsa: Boolean = {
    val noKifComments = augmentString(notationTrunc).linesIterator.toList
      .map(_.split("#|\\*").headOption.getOrElse("").trim)
      .mkString("\n")
    val lines = augmentString(noKifComments.replace(",", "\n")).linesIterator.toList
    lines.exists(l =>
      l.startsWith("PI") || l.startsWith("P1") || ImportData.csaMoveOrDropRegex.matches(l),
    )
  }

  private def parseNotation: Validated[String, ParsedNotation] =
    if (isCsa)
      CsaParser.full(notationTrunc).leftMap("CSA parsing error: " + _)
    else
      KifParser.full(notationTrunc).leftMap("KIF parsing error: " + _)

  def preprocess(user: Option[String]): Validated[String, Preprocessed] =
    parseNotation andThen { parsed =>
      Replay
        .fromParsedNotation(
          parsed,
          parsedSteps => parsedSteps.copy(value = parsedSteps.value take maxPlies),
        ) match {
        case Replay.Result.InvalidSfen(err) => Validated.invalid(err)
        case Replay.Result.Complete(position) =>
          Validated.valid(preprocessed(user, parsed, position))
        case Replay.Result.Incomplete(position, _) =>
          Validated.valid(preprocessed(user, parsed, position))
      }
    }

  private def preprocessed(
      user: Option[String],
      parsed: ParsedNotation,
      position: Position,
  ): Preprocessed = {
    val init =
      Position(parsed.initialSfen, parsed.variant)
    val status = createStatus(~parsed.tags(_.Termination).map(_.toUpperCase))

    val date = parsed.tags.anyDate

    def name(whichName: TagPicker, whichRating: TagPicker): String =
      parsed.tags(whichName).fold("?") { n =>
        n + ~parsed.tags(whichRating).map(e => s" (${e take 8})")
      }

    val notationTrunc = notation.take(maxLength)

    val dbGame = Game
      .make(
        position = position,
        initialSfen = init.toSfen.some,
        sentePlayer = Player.make(shogi.Sente) withName name(_.Sente, _.SenteElo),
        gotePlayer = Player.make(shogi.Gote) withName name(_.Gote, _.GoteElo),
        mode = Mode.Casual,
        proMode = false,
        source = Source.Import,
        clock = lila.game.clock.NotationClock.config(parsed.tags).map(_.toClock(position.color)),
        notationImport = NotationImport
          .make(
            user = user,
            date = date,
            kif = !isCsa option notationTrunc,
            csa = isCsa option notationTrunc,
          )
          .some,
      )
      .sloppy
      .copy(loadClockHistory = _ => None)
      .start pipe { dbGame =>
      // apply the result from the board or the tags
      position.status match {
        case Some(positionStatus) => dbGame.finish(positionStatus, position.winner)
        case None =>
          parsed.tags.resultColor
            .map(winner => TagResult(status, winner))
            .filter(_.status > Status.Started)
            .fold(dbGame) { res =>
              dbGame.finish(res.status, res.winner)
            }
      }
    }

    Preprocessed(NewGame(dbGame), init, parsed)
  }
}

object ImportData {

  // mirrors the CSA step regex in scalashogi, which is no longer public
  private val csaMoveOrDropRegex =
    raw"""(\+|-)?([1-9][1-9]|00)([1-9][1-9])(OU|HI|RY|KA|UM|KI|GI|NG|KE|NK|KY|NY|FU|TO)""".r
}
