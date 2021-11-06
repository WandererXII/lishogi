package lishogi.teamSearch

import akka.actor._
import com.softwaremill.macwire._
import io.methvin.play.autoconfig._
import play.api.Configuration

import lishogi.common.config._
import lishogi.search._

@Module
private class TeamSearchConfig(
    @ConfigName("index") val indexName: String,
    @ConfigName("actor.name") val actorName: String
)

final class Env(
    appConfig: Configuration,
    makeClient: Index => ESClient,
    teamRepo: lishogi.team.TeamRepo
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: ActorSystem
) {

  private val config = appConfig.get[TeamSearchConfig]("teamSearch")(AutoConfig.loader)

  private val maxPerPage = MaxPerPage(15)

  private lazy val client = makeClient(Index(config.indexName))

  private lazy val paginatorBuilder = wire[lishogi.search.PaginatorBuilder[lishogi.team.Team, Query]]

  lazy val api: TeamSearchApi = wire[TeamSearchApi]

  def apply(text: String, page: Int) = paginatorBuilder(Query(text), page)

  def cli =
    new lishogi.common.Cli {
      def process = { case "team" :: "search" :: "reset" :: Nil =>
        api.reset inject "done"
      }
    }

  system.actorOf(
    Props(new Actor {
      import lishogi.team.actorApi._
      def receive = {
        case InsertTeam(team) => api store team
        case RemoveTeam(id)   => client deleteById Id(id)
      }
    }),
    name = config.actorName
  )
}
