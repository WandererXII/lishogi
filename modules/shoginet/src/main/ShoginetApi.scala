package lila.shoginet

import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.joda.time.DateTime
import reactivemongo.api.bson._

import lila.common.IpAddress
import lila.db.dsl._
import lila.shoginet.Client.Skill

final class ShoginetApi(
    repo: ShoginetRepo,
    moveDb: MoveDB,
    analysisBuilder: AnalysisBuilder,
    colls: ShoginetColls,
    monitor: Monitor,
    sink: lila.analyse.Analyser,
    puzzles: lila.puzzle.PuzzleApi,
    socketExists: String => Fu[Boolean],
    config: ShoginetApi.Config,
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: akka.actor.ActorSystem,
) {

  import BSONHandlers._
  import JsonApi.Request.CompleteAnalysis
  import JsonApi.Request.PartialAnalysis
  import ShoginetApi._

  private val workQueue =
    new lila.hub.DuctSequencer(maxSize = 256, timeout = 5 seconds, name = "shoginetApi")

  def keyExists(key: Client.Key) = repo.getEnabledClient(key).map(_.isDefined)

  def authenticateClient(req: JsonApi.Request, ip: IpAddress): Fu[Try[Client]] = {
    if (config.anonMode && req.shoginet.apikey.value.isEmpty) repo.getAnonClient map some
    else repo.getEnabledClient(req.shoginet.apikey)
  } map {
    case None => Failure(new Exception("Can't authenticate: invalid key or disabled client"))
    case Some(client) => config.clientVersion accept req.shoginet.version map (_ => client)
  } flatMap {
    case Success(client) => repo.updateClientInstance(client, req instance ip) map Success.apply
    case failure         => fuccess(failure)
  }

  def acquire(client: Client, slow: Boolean = false): Fu[Option[JsonApi.WorkPayload]] =
    (client.skill match {
      case Skill.Move | Skill.MoveStd => acquireMove(client)
      case Skill.Analysis             => acquireAnalysis(client, slow)
      case Skill.Puzzle               => acquirePuzzle(client)
      case Skill.All =>
        acquireMove(client) orElse acquireAnalysis(client, slow) orElse {
          !client.isAnon ?? acquirePuzzle(client)
        }
    }).monSuccess(_.shoginet.acquire)
      .recover { case e: Exception =>
        logger.error("Shoginet.acquire", e)
        none
      }

  private def acquireMove(client: Client): Fu[Option[JsonApi.WorkPayload]] =
    moveDb.acquire(client) map { _ map JsonApi.move }

  private def acquireAnalysis(client: Client, slow: Boolean): Fu[Option[JsonApi.WorkPayload]] =
    workQueue {
      colls.analysis
        .find(
          $doc(
            "acquired" $exists false,
            "lastTryByKey" $ne client.key, // client alternation
          ) ++ {
            slow ?? $doc("sender.system" -> true)
          },
        )
        .sort(
          $doc(
            "sender.system" -> 1, // user requests first, then lishogi auto analysis
            "createdAt"     -> 1,
          ),
        )
        .one[Work.Analysis]
        .flatMap {
          _ ?? { work =>
            repo.updateAnalysis(work assignTo client) inject work.some
          }
        }
    }.map { _ map JsonApi.analysis(config.analysisNodes) }

  private def acquirePuzzle(client: Client): Fu[Option[JsonApi.WorkPayload]] =
    workQueue {
      colls.puzzle
        .find(
          $doc(
            "acquired" $exists false,
            "lastTryByKey" $ne client.key, // client alternation
          ),
        )
        .sort($doc("createdAt" -> 1))
        .one[Work.Puzzle]
        .flatMap {
          _ ?? { work =>
            repo.updatePuzzle(work assignTo client) inject work.some
          }
        }
    }.map { _ map JsonApi.puzzle }

  def postMove(workId: Work.Id, client: Client, data: JsonApi.Request.PostMove): Funit =
    fuccess {
      moveDb.postResult(workId, client, data)
    }

  def postAnalysis(
      workId: Work.Id,
      client: Client,
      data: JsonApi.Request.PostAnalysis,
  ): Fu[PostAnalysisResult] =
    repo
      .getAnalysis(workId)
      .flatMap {
        case None =>
          Monitor.notFound(workId, "analysis", client)
          fufail(WorkNotFound)
        case Some(work) if work isAcquiredBy client =>
          data.completeOrPartial match {
            case complete: CompleteAnalysis =>
              {
                analysisBuilder(client, work, complete.analysis) flatMap { analysis =>
                  monitor.analysis(work, client, complete)
                  repo.deleteAnalysis(work) inject PostAnalysisResult.Complete(work, analysis)
                }
              } recoverWith { case e: Exception =>
                Monitor.failure(work, client, e)
                repo.updateOrGiveUpAnalysis(work.invalid) >> fufail(e)
              }
            case partial: PartialAnalysis =>
              {
                fuccess(work.game.studyId.isDefined) >>| socketExists(work.game.id)
              } flatMap {
                case true =>
                  analysisBuilder.partial(client, work, partial.analysis) map { analysis =>
                    PostAnalysisResult.Partial(analysis)
                  }
                case false => fuccess(PostAnalysisResult.UnusedPartial)
              }
          }
        case Some(work) =>
          Monitor.notAcquired(work, client)
          fufail(NotAcquired)
      }
      .chronometer
      .logIfSlow(200, logger) {
        case PostAnalysisResult.Complete(_, res) => s"post analysis for ${res.id}"
        case PostAnalysisResult.Partial(res)     => s"partial analysis for ${res.id}"
        case PostAnalysisResult.UnusedPartial    => s"unused partial analysis"
      }
      .result
      .flatMap {
        case r @ PostAnalysisResult.Complete(work, res) => {
          val puzs = PuzzleFinder(work, res)
          repo.addPuzzles(puzs) >> sink.save(res).inject(r)
        }
        case r @ PostAnalysisResult.Partial(res)  => sink progress res inject r
        case r @ PostAnalysisResult.UnusedPartial => fuccess(r)
      }

  def abort(workId: Work.Id, client: Client): Funit =
    workQueue {
      repo.getAnalysis(workId).map(_.filter(_ isAcquiredBy client)) flatMap {
        _ ?? { work =>
          Monitor.abort(client)
          repo.updateAnalysis(work.abort)
        }
      }
    }

  def userAnalysisExists(gameId: String) =
    colls.analysis.exists(
      $doc(
        "game.id"       -> gameId,
        "sender.system" -> false,
      ),
    )

  def postPuzzle(
      workId: Work.Id,
      client: Client,
      data: JsonApi.Request.PostPuzzle,
  ): Funit =
    repo
      .getPuzzle(workId)
      .flatMap {
        case None =>
          Monitor.notFound(workId, "puzzle", client)
          fufail(WorkNotFound)
        case Some(work) if work isAcquiredBy client =>
          (data.puzzle ?? { puz =>
            puzzles.submissions
              .addNew(
                sfen = puz.sfen,
                line = puz.line,
                themes = puz.themes,
                source = work.source.game.map(_.id).toRight(work.source.user.flatMap(_.author)),
                submittedBy = work.source.user.map(_.submittedBy),
              )
              .map2(id => Monitor.newPuzzle(id.value, client))
          }) >> repo.deletePuzzle(work)
        case Some(work) =>
          Monitor.notAcquired(work, client)
          fufail(NotAcquired)
      }

  def status =
    monitor.statusCache.get {} map { c =>
      import play.api.libs.json.Json
      def statusFor(s: Monitor.StatusFor) =
        Json.obj(
          "acquired" -> s.acquired,
          "queued"   -> s.queued,
        )
      Json.obj(
        "analysis" -> Json.obj(
          "user"   -> statusFor(c.user),
          "system" -> statusFor(c.system),
        ),
        "puzzles" -> statusFor(c.puzzles),
      )
    }

  def addPuzzles(
      sfens: List[shogi.format.forsyth.Sfen],
      source: Option[String],
      submittedBy: String,
  ): Funit = {
    val puzs = sfens
      .flatMap(sfen => sfen.toSituation(shogi.variant.Standard))
      .withFilter(
        _.playable(true, true),
      )
      .map { sit =>
        Work.Puzzle(
          _id = Work.makeId,
          game = Work.Game(
            id = "synthetic",
            initialSfen = sit.toSfen.some,
            studyId = none,
            variant = sit.variant,
            moves = "",
          ),
          engine =
            if (forYaneuraOu(sit)) lila.game.EngineConfig.Engine.YaneuraOu.name
            else lila.game.EngineConfig.Engine.Fairy.name,
          source = Work.Puzzle.Source(
            game = none,
            user = Work.Puzzle.Source
              .FromUser(
                submittedBy = submittedBy,
                author = source,
              )
              .some,
          ),
          tries = 0,
          lastTryByKey = none,
          acquired = none,
          createdAt = DateTime.now,
        )
      }
    repo.addPuzzles(puzs)
  }

  private val initialSit = shogi.Situation(shogi.variant.Standard)
  private def forYaneuraOu(sit: shogi.Situation): Boolean =
    sit.variant.handRoles.forall { r =>
      sit.board.count(r) + sit.hands.sente(r) + sit.hands.gote(r) <= initialSit.board.count(
        r,
      ) + initialSit.hands.sente(r) + initialSit.hands.gote(r)
    } && sit.variant.allRoles.diff(sit.variant.handRoles).forall { r =>
      sit.board.count(r) <= initialSit.board.count(r)
    }

  def queuedPuzzles(userId: String): Fu[Int] =
    repo.countUserPuzzles(userId)

  private[shoginet] def createClient(userId: Client.UserId): Fu[Client] = {
    val client = Client(
      _id = Client.makeKey,
      userId = userId,
      skill = Skill.Analysis,
      instance = None,
      enabled = true,
      createdAt = DateTime.now,
    )
    repo addClient client inject client
  }
}

object ShoginetApi {

  import lila.base.LilaException

  case class Config(
      anonMode: Boolean,
      analysisNodes: Int,
      clientVersion: Client.ClientVersion,
  )

  case object WorkNotFound extends LilaException {
    val message = "The work has disappeared"
  }

  case object GameNotFound extends LilaException {
    val message = "The game has disappeared"
  }

  case object NotAcquired extends LilaException {
    val message = "The work was distributed to someone else"
  }

  sealed trait PostAnalysisResult
  object PostAnalysisResult {
    case class Complete(work: Work.Analysis, analysis: lila.analyse.Analysis)
        extends PostAnalysisResult
    case class Partial(analysis: lila.analyse.Analysis) extends PostAnalysisResult
    case object UnusedPartial                           extends PostAnalysisResult
  }
}
