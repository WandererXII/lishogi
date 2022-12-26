package lila.fishnet

import org.joda.time.DateTime
import play.api.libs.json._

import shogi.format.forsyth.Sfen
import shogi.format.usi.{ UciToUsi, Usi }
import shogi.variant.Variant
import shogi.StartingPosition

import lila.common.{ IpAddress, Maths }
import lila.common.Json._
import lila.fishnet.{ Work => W }
import lila.tree.Eval.JsonHandlers._
import lila.tree.Eval.{ Cp, Mate }

object JsonApi {

  sealed trait Request {
    val shoginet: Request.Fishnet
    val yaneuraou: Request.Engine
    val fairy: Request.Engine

    def instance(ip: IpAddress) =
      Client.Instance(
        shoginet.version,
        shoginet.python | Client.Python(""),
        Client.Engines(
          yaneuraou = Client.Engine(yaneuraou.name),
          fairy = Client.Engine(fairy.name)
        ),
        ip,
        DateTime.now
      )
  }

  object Request {

    sealed trait Result

    case class Fishnet(
        version: Client.Version,
        python: Option[Client.Python],
        apikey: Client.Key
    )

    sealed trait Engine {
      def name: String
    }

    case class BaseEngine(name: String) extends Engine

    case class FullEngine(
        name: String,
        options: EngineOptions
    ) extends Engine

    case class EngineOptions(
        threads: Option[String],
        hash: Option[String]
    ) {
      def threadsInt = threads flatMap (_.toIntOption)
      def hashInt    = hash flatMap (_.toIntOption)
    }

    case class Acquire(
        shoginet: Fishnet,
        yaneuraou: BaseEngine,
        fairy: BaseEngine
    ) extends Request

    case class PostMove(
        shoginet: Fishnet,
        yaneuraou: FullEngine,
        fairy: FullEngine,
        move: MoveResult
    ) extends Request
        with Result {}

    case class MoveResult(bestmove: String) {
      def usi: Option[Usi] = Usi(bestmove).orElse(UciToUsi(bestmove))
    }

    case class PostAnalysis(
        shoginet: Fishnet,
        yaneuraou: FullEngine,
        fairy: FullEngine,
        analysis: List[Option[Evaluation.OrSkipped]]
    ) extends Request
        with Result {

      def completeOrPartial =
        if (analysis.headOption.??(_.isDefined))
          CompleteAnalysis(shoginet, yaneuraou, fairy, analysis.flatten)
        else PartialAnalysis(shoginet, yaneuraou, fairy, analysis)
    }

    case class CompleteAnalysis(
        shoginet: Fishnet,
        yaneuraou: FullEngine,
        fairy: FullEngine,
        analysis: List[Evaluation.OrSkipped]
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
        shoginet: Fishnet,
        yaneuraou: FullEngine,
        fairy: FullEngine,
        analysis: List[Option[Evaluation.OrSkipped]]
    )

    case class Evaluation(
        pv: List[Usi],
        score: Evaluation.Score,
        time: Option[Int],
        nodes: Option[Int],
        nps: Option[Int],
        depth: Option[Int]
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
  }

  case class Game(
      game_id: String,
      position: Sfen,
      variant: Variant,
      moves: String
  )

  def fromGame(g: W.Game) =
    Game(
      game_id = if (g.studyId.isDefined) "" else g.id,
      position = g.initialSfen | g.variant.initialSfen,
      variant = g.variant,
      moves = g.moves
    )

  sealed trait Work {
    val id: String
    val game: Game
  }

  case class Move(
      id: String,
      level: Int,
      game: Game,
      clock: Option[Work.Clock]
  ) extends Work

  def moveFromWork(m: Work.Move) =
    Move(m.id.value, m.level, fromGame(m.game), m.clock)

  case class Analysis(
      id: String,
      game: Game,
      nodes: Int,
      skipPositions: List[Int]
  ) extends Work

  def analysisFromWork(nodes: Int)(m: Work.Analysis) =
    Analysis(
      id = m.id.value,
      game = fromGame(m.game),
      nodes = nodes,
      skipPositions = m.skipPositions
    )

  object readers {
    import play.api.libs.functional.syntax._
    implicit val ClientVersionReads = Reads.of[String].map(new Client.Version(_))
    implicit val ClientPythonReads  = Reads.of[String].map(new Client.Python(_))
    implicit val ClientKeyReads     = Reads.of[String].map(new Client.Key(_))
    implicit val EngineOptionsReads = Json.reads[Request.EngineOptions]
    implicit val BaseEngineReads    = Json.reads[Request.BaseEngine]
    implicit val FullEngineReads    = Json.reads[Request.FullEngine]
    implicit val FishnetReads       = Json.reads[Request.Fishnet]
    implicit val AcquireReads       = Json.reads[Request.Acquire]
    implicit val MoveResultReads    = Json.reads[Request.MoveResult]
    implicit val PostMoveReads      = Json.reads[Request.PostMove]
    implicit val ScoreReads         = Json.reads[Request.Evaluation.Score]
    implicit val usiListReads = Reads.of[String] map { str =>
      ~(Usi.readList(str).orElse(UciToUsi.readList(str)))
    }

    implicit val EvaluationReads: Reads[Request.Evaluation] = (
      (__ \ "pv").readNullable[List[Usi]].map(~_) and
        (__ \ "score").read[Request.Evaluation.Score] and
        (__ \ "time").readNullable[Int] and
        (__ \ "nodes").readNullable[Long].map(_.map(_.toSaturatedInt)) and
        (__ \ "nps").readNullable[Long].map(_.map(_.toSaturatedInt)) and
        (__ \ "depth").readNullable[Int]
    )(Request.Evaluation.apply _)
    implicit val EvaluationOptionReads = Reads[Option[Request.Evaluation.OrSkipped]] {
      case JsNull => JsSuccess(None)
      case obj =>
        if (~(obj boolean "skipped")) JsSuccess(Left(Request.Evaluation.Skipped).some)
        else EvaluationReads reads obj map Right.apply map some
    }
    implicit val PostAnalysisReads: Reads[Request.PostAnalysis] = Json.reads[Request.PostAnalysis]
  }

  object writers {
    implicit val VariantWrites = Writes[Variant] { v =>
      JsString(v.key)
    }
    implicit val ClockWrites: Writes[Work.Clock] = Json.writes[Work.Clock]
    implicit val GameWrites: Writes[Game]        = Json.writes[Game]
    implicit val WorkIdWrites = Writes[Work.Id] { id =>
      JsString(id.value)
    }
    private def fairyOrYane(sfen: Sfen, variant: Variant) =
      if (
        variant.standard && Some(sfen)
          .filterNot(_.initialOf(variant))
          .fold(true)(StartingPosition isHandicap _)
      ) None
      else Some("fairy")

    implicit val WorkWrites = OWrites[Work] { work =>
      (work match {
        case a: Analysis =>
          Json.obj(
            "work" -> Json.obj(
              "type"   -> "analysis",
              "id"     -> a.id,
              "flavor" -> fairyOrYane(a.game.position, a.game.variant)
            ),
            "nodes"         -> a.nodes,
            "skipPositions" -> a.skipPositions
          )
        case m: Move =>
          Json.obj(
            "work" -> Json.obj(
              "type"   -> "move",
              "id"     -> m.id,
              "level"  -> m.level,
              "clock"  -> m.clock,
              "flavor" -> fairyOrYane(m.game.position, m.game.variant)
            )
          )
      }) ++ Json.toJson(work.game).as[JsObject]
    }
  }
}
