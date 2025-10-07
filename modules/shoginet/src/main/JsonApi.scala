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
import lila.game.FairyConversion.Kyoto
import lila.shoginet.{ Work => W }
import lila.tree.Eval.Cp
import lila.tree.Eval.JsonHandlers._
import lila.tree.Eval.Mate

object JsonApi {

  sealed trait Request {
    val shoginet: Request.Shoginet
    val yaneuraou: Request.Engine
    val fairy: Request.Engine

    def instance(ip: IpAddress) =
      Client.Instance(
        shoginet.version,
        shoginet.python | Client.Python(""),
        Client.Engines(
          yaneuraou = Client.Engine(yaneuraou.name),
          fairy = Client.Engine(fairy.name),
        ),
        ip,
        DateTime.now,
      )
  }

  object Request {

    sealed trait Result

    case class Shoginet(
        version: Client.Version,
        python: Option[Client.Python],
        apikey: Client.Key,
    )

    sealed trait Engine {
      def name: String
    }

    case class BaseEngine(name: String) extends Engine

    case class FullEngine(
        name: String,
        options: EngineOptions,
    ) extends Engine

    case class EngineOptions(
        threads: Option[String],
        hash: Option[String],
    ) {
      def threadsInt = threads flatMap (_.toIntOption)
      def hashInt    = hash flatMap (_.toIntOption)
    }

    case class Acquire(
        shoginet: Shoginet,
        yaneuraou: BaseEngine,
        fairy: BaseEngine,
    ) extends Request

    case class PostMove(
        shoginet: Shoginet,
        yaneuraou: FullEngine,
        fairy: FullEngine,
        move: MoveResult,
    ) extends Request
        with Result {}

    case class MoveResult(bestmove: String) {
      def usi(variant: Variant): Option[Usi] = {
        if (variant.kyotoshogi) Kyoto.readFairyUsi(bestmove)
        else Usi(bestmove).orElse(UciToUsi(bestmove))
      }
    }

    case class PostAnalysis(
        shoginet: Shoginet,
        yaneuraou: FullEngine,
        fairy: FullEngine,
        analysis: List[Option[Evaluation.OrSkipped]],
    ) extends Request
        with Result {

      def completeOrPartial =
        if (analysis.headOption.??(_.isDefined))
          CompleteAnalysis(shoginet, yaneuraou, fairy, analysis.flatten)
        else PartialAnalysis(shoginet, yaneuraou, fairy, analysis)
    }

    case class CompleteAnalysis(
        shoginet: Shoginet,
        yaneuraou: FullEngine,
        fairy: FullEngine,
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
        yaneuraou: FullEngine,
        fairy: FullEngine,
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
        yaneuraou: FullEngine,
        fairy: FullEngine,
        result: Boolean,
    ) extends Request
        with Result {}

    case class PostPuzzleVerified(
        shoginet: Shoginet,
        yaneuraou: FullEngine,
        fairy: FullEngine,
        result: Option[CompletedPuzzle],
    ) extends Request
        with Result {}

    case class CompletedPuzzle(
        sfen: Sfen,
        line: List[Usi],
        ambiguousPromotions: List[Int],
        themes: List[String],
    )
  }

  case class Game(
      game_id: String,
      position: Sfen,
      variant: Variant,
      moves: String,
  )

  def fromGame(g: W.Game) =
    if (g.variant.kyotoshogi) kyotoFromGame(g)
    else
      Game(
        game_id = if (g.studyId.isDefined) "" else g.id,
        position = g.initialSfen | g.variant.initialSfen,
        variant = g.variant,
        moves = g.moves,
      )

  private def kyotoFromGame(g: W.Game) =
    Game(
      game_id = if (g.studyId.isDefined) "" else g.id,
      position = Kyoto.makeFairySfen(g.initialSfen | g.variant.initialSfen),
      variant = g.variant,
      moves = Kyoto.makeFairyUsiList(g.usiList, g.initialSfen).mkString(" "),
    )

  sealed trait Work {
    val id: String
    val game: Game
    val engine: String
  }

  case class Move(
      id: String,
      level: Int,
      game: Game,
      engine: String,
      clock: Option[Work.Clock],
  ) extends Work

  def moveFromWork(m: Work.Move) =
    Move(
      id = m.id.value,
      level = m.level,
      game = fromGame(m.game),
      engine = m.engine,
      clock = m.clock,
    )

  case class Analysis(
      id: String,
      game: Game,
      engine: String,
      nodes: Int,
      skipPositions: List[Int],
  ) extends Work

  def analysisFromWork(nodes: Int)(a: Work.Analysis) =
    Analysis(
      id = a.id.value,
      game = fromGame(a.game),
      engine = a.engine,
      nodes = nodes,
      skipPositions = a.skipPositions,
    )

  case class Puzzle(
      id: String,
      game: Game,
      engine: String,
  ) extends Work

  def puzzleFromWork(p: Work.Puzzle) =
    Puzzle(
      id = p.id.value,
      game = fromGame(p.game),
      engine = p.engine,
    )

  object readers {
    import play.api.libs.functional.syntax._
    implicit val ClientVersionReads: Reads[Client.Version] =
      Reads.of[String].map(new Client.Version(_))
    implicit val ClientPythonReads: Reads[Client.Python] =
      Reads.of[String].map(new Client.Python(_))
    implicit val ClientKeyReads: Reads[Client.Key] = Reads.of[String].map(new Client.Key(_))
    implicit val EngineOptionsReads: Reads[Request.EngineOptions] =
      Json.reads[Request.EngineOptions]
    implicit val BaseEngineReads: Reads[Request.BaseEngine]  = Json.reads[Request.BaseEngine]
    implicit val FullEngineReads: Reads[Request.FullEngine]  = Json.reads[Request.FullEngine]
    implicit val ShoginetReads: Reads[Request.Shoginet]      = Json.reads[Request.Shoginet]
    implicit val AcquireReads: Reads[Request.Acquire]        = Json.reads[Request.Acquire]
    implicit val MoveResultReads: Reads[Request.MoveResult]  = Json.reads[Request.MoveResult]
    implicit val PostMoveReads: Reads[Request.PostMove]      = Json.reads[Request.PostMove]
    implicit val ScoreReads: Reads[Request.Evaluation.Score] = Json.reads[Request.Evaluation.Score]
    implicit val usiListReads: Reads[List[Usi]] = Reads.of[String] map { str =>
      ~(Usi.readList(str).orElse(UciToUsi.readList(str)).orElse(Kyoto.readFairyUsiList(str)))
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
    implicit val PostPuzzleReads: Reads[Request.PostPuzzle]     = Json.reads[Request.PostPuzzle]

    implicit val CompletedPuzzleReads: Reads[Request.CompletedPuzzle] =
      Json.reads[Request.CompletedPuzzle]
    implicit val PostPuzzleVerified: Reads[Request.PostPuzzleVerified] =
      Json.reads[Request.PostPuzzleVerified]
  }

  object writers {
    implicit val VariantWrites: Writes[Variant] = Writes[Variant] { v =>
      JsString(v.key)
    }
    implicit val ClockWrites: Writes[Work.Clock] = Json.writes[Work.Clock]
    implicit val GameWrites: Writes[Game]        = Json.writes[Game]
    implicit val WorkIdWrites: Writes[W.Id] = Writes[Work.Id] { id =>
      JsString(id.value)
    }

    implicit val WorkWrites: OWrites[Work] = OWrites[Work] { work =>
      (work match {
        case a: Analysis =>
          Json.obj(
            "work" -> Json.obj(
              "type"   -> "analysis",
              "id"     -> a.id,
              "flavor" -> a.engine,
            ),
            "nodes"         -> a.nodes,
            "skipPositions" -> a.skipPositions,
          )
        case m: Move =>
          Json.obj(
            "work" -> Json.obj(
              "type"   -> "move",
              "id"     -> m.id,
              "level"  -> m.level,
              "clock"  -> m.clock,
              "flavor" -> m.engine,
            ),
          )
        case p: Puzzle =>
          Json.obj(
            "work" -> Json.obj(
              "type"   -> "puzzle",
              "id"     -> p.id,
              "flavor" -> p.engine,
            ),
          )
      }) ++ Json.toJson(work.game).as[JsObject]
    }
  }
}
