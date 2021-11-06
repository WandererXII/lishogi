package lishogi.explorer

import com.softwaremill.macwire._
import play.api.Configuration

case class InternalEndpoint(value: String) extends AnyVal with StringValue

@Module
final class Env(
    appConfig: Configuration,
    gameRepo: lishogi.game.GameRepo,
    userRepo: lishogi.user.UserRepo,
    gameImporter: lishogi.importer.Importer,
    getBotUserIds: lishogi.user.GetBotIds,
    settingStore: lishogi.memo.SettingStore.Builder,
    ws: play.api.libs.ws.WSClient
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: akka.actor.ActorSystem
) {

  private lazy val internalEndpoint = InternalEndpoint {
    appConfig.get[String]("explorer.internal_endpoint")
  }

  private lazy val indexer: ExplorerIndexer = wire[ExplorerIndexer]

  lazy val importer = wire[ExplorerImporter]

  def cli =
    new lishogi.common.Cli {
      def process = { case "explorer" :: "index" :: since :: Nil =>
        indexer(since) inject "done"
      }
    }

  lazy val indexFlowSetting = settingStore[Boolean](
    "explorerIndexFlow",
    default = false,
    text = "Explorer: index new games as soon as they complete".some
  )

  lishogi.common.Bus.subscribeFun("finishGame") {
    case lishogi.game.actorApi.FinishGame(game, _, _) if !game.aborted && indexFlowSetting.get() => indexer(game)
  }
}
