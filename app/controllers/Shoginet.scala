package controllers

import scala.util.Failure
import scala.util.Success

import play.api.libs.json._
import play.api.mvc._

import lila.app._
import lila.common.HTTPRequest
import lila.shoginet.JsonApi
import lila.shoginet.JsonApi.readers._
import lila.shoginet.JsonApi.writers._
import lila.shoginet.Work

final class Shoginet(env: Env) extends LilaController(env) {

  private def api    = env.shoginet.api
  private val logger = lila.log("shoginet")

  def acquire(slow: Boolean = false) =
    ClientAction[JsonApi.Request.Acquire] { _ => client =>
      api.acquire(client, slow) addEffect { jobOpt =>
        lila.mon.shoginet.http.request(jobOpt.isDefined).increment().unit
      } map Right.apply
    }

  def move(workId: String) =
    ClientAction[JsonApi.Request.PostMove] { data => client =>
      api.postMove(Work.Id(workId), client, data) >>
        api.acquire(client).map(Right.apply)
    }

  def analysis(workId: String, slow: Boolean = false, stop: Boolean = false) =
    ClientAction[JsonApi.Request.PostAnalysis] { data => client =>
      import lila.shoginet.ShoginetApi._
      def onComplete =
        if (stop) fuccess(Left(NoContent))
        else api.acquire(client, slow) map Right.apply
      api
        .postAnalysis(Work.Id(workId), client, data)
        .flatFold(
          {
            case WorkNotFound => onComplete
            case GameNotFound => onComplete
            case NotAcquired  => onComplete
            case e            => fuccess(Left(InternalServerError(e.getMessage)))
          },
          {
            case PostAnalysisResult.Complete(_, analysis) =>
              env.round.proxyRepo.updateIfPresent(analysis.id)(_.setAnalysed)
              onComplete
            case _: PostAnalysisResult.Partial    => fuccess(Left(NoContent))
            case PostAnalysisResult.UnusedPartial => fuccess(Left(NoContent))
          },
        )
    }

  def abort(workId: String) =
    ClientAction[JsonApi.Request.Acquire] { _ => client =>
      api.abort(Work.Id(workId), client) inject Right(none)
    }

  def puzzle(workId: String) =
    ClientAction[JsonApi.Request.PostPuzzle] { data => client =>
      api.postPuzzle(Work.Id(workId), client, data) >>
        api.acquire(client).map(Right.apply)
    }

  def keyExists(key: String) =
    Action.async { _ =>
      api keyExists lila.shoginet.Client.Key(key) map {
        case true  => Ok
        case false => NotFound
      }
    }

  def status =
    Action.async {
      api.status map { Ok(_) }
    }

  private def ClientAction[A <: JsonApi.Request](
      f: A => lila.shoginet.Client => Fu[Either[Result, Option[JsonApi.WorkPayload]]],
  )(implicit reads: Reads[A]) =
    Action.async(parse.tolerantJson) { req =>
      req.body
        .validate[A]
        .fold(
          err => {
            logger.warn(s"Malformed request: $err\n${req.body}")
            BadRequest(jsonError(JsError toJson err)).fuccess
          },
          data =>
            api.authenticateClient(data, HTTPRequest lastRemoteAddress req) flatMap {
              case Failure(msg) => Unauthorized(jsonError(msg.getMessage)).fuccess
              case Success(client) =>
                f(data)(client).map {
                  case Right(Some(work)) => Accepted(Json toJson work)
                  case Right(None)       => NoContent
                  case Left(result)      => result
                }
            },
        )
    }
}
