package lishogi.studySearch

import akka.actor._
import com.softwaremill.macwire._
import scala.concurrent.duration._

import lishogi.common.Bus
import lishogi.common.paginator._
import lishogi.hub.actorApi.study.RemoveStudy
import lishogi.hub.LateMultiThrottler
import lishogi.search._
import lishogi.study.Study
import lishogi.user.User

final class Env(
    studyRepo: lishogi.study.StudyRepo,
    chapterRepo: lishogi.study.ChapterRepo,
    pager: lishogi.study.StudyPager,
    makeClient: Index => ESClient
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: ActorSystem,
    mat: akka.stream.Materializer
) {

  private val client = makeClient(Index("study"))

  private val indexThrottler = LateMultiThrottler(executionTimeout = 3.seconds.some, logger = logger)

  val api: StudySearchApi = wire[StudySearchApi]

  def apply(me: Option[User])(text: String, page: Int) =
    Paginator[Study.WithChaptersAndLiked](
      adapter = new AdapterLike[Study] {
        def query                           = Query(text, me.map(_.id))
        def nbResults                       = api count query
        def slice(offset: Int, length: Int) = api.search(query, From(offset), Size(length))
      } mapFutureList pager.withChaptersAndLiking(me),
      currentPage = page,
      maxPerPage = pager.maxPerPage
    )

  def cli =
    new lishogi.common.Cli {
      def process = {
        case "study" :: "search" :: "reset" :: Nil          => api.reset("reset") inject "done"
        case "study" :: "search" :: "index" :: since :: Nil => api.reset(since) inject "done"
      }
    }

  Bus.subscribeFun("study") {
    case lishogi.study.actorApi.SaveStudy(study) => api store study
    case RemoveStudy(id, _)                   => client deleteById Id(id)
  }
}
