package lishogi.gameSearch

import akka.actor.ActorSystem
import com.softwaremill.macwire._
import io.methvin.play.autoconfig._
import play.api.Configuration

import lishogi.game.actorApi.{ FinishGame, InsertGame }
import lishogi.search._
import lishogi.common.config._

@Module
private class GameSearchConfig(
    @ConfigName("index") val indexName: String,
    @ConfigName("paginator.max_per_page") val paginatorMaxPerPage: MaxPerPage,
    @ConfigName("actor.name") val actorName: String
)

@Module
final class Env(
    appConfig: Configuration,
    gameRepo: lishogi.game.GameRepo,
    makeClient: Index => ESClient
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: ActorSystem
) {

  private val config = appConfig.get[GameSearchConfig]("gameSearch")(AutoConfig.loader)

  private lazy val client = makeClient(Index(config.indexName))

  lazy val api = wire[GameSearchApi]

  lazy val paginator = wire[PaginatorBuilder[lishogi.game.Game, Query]]

  lazy val forms = wire[DataForm]

  lazy val userGameSearch = wire[UserGameSearch]

  lishogi.common.Bus.subscribeFun("finishGame", "gameSearchInsert") {
    case FinishGame(game, _, _) if !game.aborted => api store game
    case InsertGame(game)                        => api store game
  }
}
