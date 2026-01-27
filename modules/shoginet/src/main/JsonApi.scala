package lila.shoginet

import play.api.libs.json._

import org.joda.time.DateTime

import shogi.format.forsyth.Sfen
import shogi.format.usi.UciToUsi
import shogi.format.usi.Usi
import shogi.variant.Variant

import lila.common.IpAddress
import lila.common.Json._
import lila.common.Maths
import lila.game.FairyConversion
import lila.shoginet.{ Work => W }
import lila.tree.Eval.Cp
import lila.tree.Eval.JsonHandlers._
import lila.tree.Eval.Mate

object JsonApi {

  sealed trait Request {
    val shoginet: Request.Shoginet

    def instance(ip: IpAddress) =
      Client.Instance(
        shoginet.version,
        ip,
        DateTime.now,
      )
  }

  object Request {

    sealed trait Result

    case class Shoginet(
        version: Client.Version,
        apikey: Client.Key,
    )

    case class Acquire(
        shoginet: Shoginet,
    ) extends Request

    case class PostMove(
        shoginet: Shoginet,
        move: MoveResult,
    ) extends Request
        with Result {}

    case class MoveResult(bestmove: String) {
      def usi(variant: Variant): Option[Usi] = {
        FairyConversion.readFairyUsi(variant, bestmove) orElse Usi(bestmove) orElse UciToUsi(
          bestmove,
        )
      }
    }

    case class PostAnalysis(
        shoginet: Shoginet,
        analysis: List[Option[Evaluation.OrSkipped]],
    ) extends Request
        with Result {

      def completeOrPartial =
        if (analysis.headOption.??(_.isDefined))
          CompleteAnalysis(shoginet, analysis.flatten)
        else PartialAnalysis(shoginet, analysis)
    }

    case class CompleteAnalysis(
        shoginet: Shoginet,
        analysis: List[Evaluation.OrSkipped],
    ) {

      def evaluations = analysis.collect { case Right(e) => e }

      def medianNodes =
        Maths.median {
          evaluations
            .filterNot(_.mateFound)
            .filterNot(_.deadDraw)
            .flatMap(_.nodes)
        }

    }

    case class PartialAnalysis(
        shoginet: Shoginet,
        analysis: List[Option[Evaluation.OrSkipped]],
    )

    case class Evaluation(
        pv: List[Usi],
        score: Evaluation.Score,
        time: Option[Int],
        nodes: Option[Int],
        nps: Option[Int],
        depth: Option[Int],
    ) {
      val cappedNps = nps.map(_ min Evaluation.npsCeil)

      val cappedPv = pv take lila.analyse.Info.LineMaxPlies

      def isCheckmate = score.mate has Mate(0)
      def mateFound   = score.mate.isDefined
      def deadDraw    = score.cp has Cp(0)
    }

    object Evaluation {

      object Skipped

      type OrSkipped = Either[Skipped.type, Evaluation]

      case class Score(cp: Option[Cp], mate: Option[Mate]) {
        def invert                  = copy(cp.map(_.invert), mate.map(_.invert))
        def invertIf(cond: Boolean) = if (cond) invert else this
      }

      val npsCeil = 10 * 1000 * 1000

    }

    case class PostPuzzle(
        shoginet: Shoginet,
        puzzle: Option[CompletedPuzzle],
    ) extends Request
        with Result {}

    case class CompletedPuzzle(
        sfen: Sfen,
        line: List[Usi],
        themes: List[String],
    )
  }

  case class Game(
      game_id: String,
      position: Sfen,
      variant: Variant,
      moves: String,
  )

  def fromGame(g: W.Game) = {
    val sfen = g.initialSfen | g.variant.initialSfen
    Game(
      game_id = if (g.studyId.isDefined) "" else g.id,
      position = FairyConversion.makeFairySfen(g.variant, sfen) | sfen,
      variant = g.variant,
      moves = FairyConversion
        .makeFairyUsiList(g.variant, g.usiList, g.initialSfen)
        .map(_.mkString(" ")) | g.moves,
    )
  }

  sealed trait WorkPayload {
    val id: String
    val game: Game
    val engine: String
  }

  case class MovePayload(
      id: String,
      level: Int,
      game: Game,
      engine: String,
      clock: Option[W.Clock],
  ) extends WorkPayload

  def move(m: W.Move) =
    MovePayload(
      id = m.id.value,
      level = m.level,
      game = fromGame(m.game),
      engine = m.engine,
      clock = m.clock,
    )

  case class AnalysisPayload(
      id: String,
      game: Game,
      engine: String,
      nodes: Int,
      skipPositions: List[Int],
  ) extends WorkPayload

  def analysis(nodes: Int)(a: W.Analysis) =
    AnalysisPayload(
      id = a.id.value,
      game = fromGame(a.game),
      engine = a.engine,
      nodes = nodes,
      skipPositions = a.skipPositions,
    )

  case class PuzzlePayload(
      id: String,
      game: Game,
      engine: String,
      source: W.Puzzle.Source,
  ) extends WorkPayload

  def puzzle(p: W.Puzzle) =
    PuzzlePayload(
      id = p.id.value,
      game = fromGame(p.game),
      engine = p.engine,
      source = p.source,
    )

  object readers {
    import play.api.libs.functional.syntax._
    implicit val ClientVersionReads: Reads[Client.Version] =
      Reads.of[String].map(new Client.Version(_))
    implicit val ClientKeyReads: Reads[Client.Key]      = Reads.of[String].map(new Client.Key(_))
    implicit val ShoginetReads: Reads[Request.Shoginet] = Json.reads[Request.Shoginet]
    implicit val AcquireReads: Reads[Request.Acquire]   = Json.reads[Request.Acquire]
    implicit val MoveResultReads: Reads[Request.MoveResult]  = Json.reads[Request.MoveResult]
    implicit val PostMoveReads: Reads[Request.PostMove]      = Json.reads[Request.PostMove]
    implicit val ScoreReads: Reads[Request.Evaluation.Score] = Json.reads[Request.Evaluation.Score]
    implicit val usiListReads: Reads[List[Usi]] = Reads.of[String] map { str =>
      ~(
        Usi.readList(str) orElse
          UciToUsi.readList(str) orElse
          FairyConversion.Kyoto.readFairyUsiList(str) orElse
          FairyConversion.Dobutsu.readFairyUsiList(str)
      )
    }

    implicit val EvaluationReads: Reads[Request.Evaluation] = (
      (__ \ "pv").readNullable[List[Usi]].map(~_) and
        (__ \ "score").read[Request.Evaluation.Score] and
        (__ \ "time").readNullable[Int] and
        (__ \ "nodes").readNullable[Long].map(_.map(_.toSaturatedInt)) and
        (__ \ "nps").readNullable[Long].map(_.map(_.toSaturatedInt)) and
        (__ \ "depth").readNullable[Int]
    )(Request.Evaluation.apply _)
    implicit val EvaluationOptionReads: Reads[Option[Request.Evaluation.OrSkipped]] =
      Reads[Option[Request.Evaluation.OrSkipped]] {
        case JsNull => JsSuccess(None)
        case obj =>
          if (~(obj boolean "skipped")) JsSuccess(Left(Request.Evaluation.Skipped).some)
          else EvaluationReads reads obj map Right.apply map some
      }
    implicit val PostAnalysisReads: Reads[Request.PostAnalysis] = Json.reads[Request.PostAnalysis]

    implicit val CompletedPuzzleReads: Reads[Request.CompletedPuzzle] =
      Json.reads[Request.CompletedPuzzle]
    implicit val PostPuzzleReads: Reads[Request.PostPuzzle] = Json.reads[Request.PostPuzzle]
  }

  object writers {
    implicit val VariantWrites: Writes[Variant] = Writes[Variant] { v =>
      JsString(v.key)
    }
    implicit val ClockWrites: Writes[W.Clock] = Json.writes[W.Clock]
    implicit val GameWrites: Writes[Game]     = Json.writes[Game]
    implicit val WorkIdWrites: Writes[W.Id] = Writes[W.Id] { id =>
      JsString(id.value)
    }

    implicit val WorkWrites: OWrites[WorkPayload] = OWrites[WorkPayload] { work =>
      (work match {
        case a: AnalysisPayload =>
          Json.obj(
            "work" -> Json.obj(
              "type"   -> "analysis",
              "id"     -> a.id,
              "engine" -> a.engine,
            ),
            "nodes"         -> a.nodes,
            "skipPositions" -> a.skipPositions,
          )
        case m: MovePayload =>
          Json.obj(
            "work" -> Json.obj(
              "type"   -> "move",
              "id"     -> m.id,
              "level"  -> m.level,
              "clock"  -> m.clock,
              "engine" -> m.engine,
            ),
          )
        case p: PuzzlePayload =>
          Json.obj(
            "work" -> Json.obj(
              "type"   -> "puzzle",
              "id"     -> p.id,
              "engine" -> p.engine,
              "source" -> Json.obj(
                "game" -> p.source.game.map(_.id),
                "user" -> p.source.user.map(_.submittedBy),
              ),
            ),
          )
      }) ++ Json.toJson(work.game).as[JsObject]
    }
  }
}
