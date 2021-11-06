package lishogi.forumSearch

import akka.actor._
import com.softwaremill.macwire._
import io.methvin.play.autoconfig._
import play.api.Configuration

import lishogi.common.config._
import lishogi.search._
import Query.jsonWriter

@Module
private class ForumSearchConfig(
    @ConfigName("index") val indexName: String,
    @ConfigName("paginator.max_per_page") val maxPerPage: MaxPerPage,
    @ConfigName("actor.name") val actorName: String
)

final class Env(
    appConfig: Configuration,
    makeClient: Index => ESClient,
    postApi: lishogi.forum.PostApi,
    postRepo: lishogi.forum.PostRepo
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: ActorSystem,
    mat: akka.stream.Materializer
) {

  private val config = appConfig.get[ForumSearchConfig]("forumSearch")(AutoConfig.loader)

  private lazy val client = makeClient(Index(config.indexName))

  lazy val api: ForumSearchApi = wire[ForumSearchApi]

  def apply(text: String, page: Int, troll: Boolean) =
    paginatorBuilder(Query(text, troll), page)

  def cli =
    new lishogi.common.Cli {
      def process = { case "forum" :: "search" :: "reset" :: Nil =>
        api.reset inject "done"
      }
    }

  private lazy val paginatorBuilder = wire[lishogi.search.PaginatorBuilder[lishogi.forum.PostView, Query]]

  system.actorOf(
    Props(new Actor {
      import lishogi.forum.actorApi._
      def receive = {
        case InsertPost(post) => api store post
        case RemovePost(id)   => client deleteById Id(id)
        case RemovePosts(ids) => client deleteByIds ids.map(Id.apply)
      }
    }),
    name = config.actorName
  )
}
