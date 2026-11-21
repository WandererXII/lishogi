package lila.timeline

import play.api.Configuration

import akka.actor._
import com.softwaremill.macwire._
import io.methvin.play.autoconfig._

import lila.common.config._

@Module
private class TimelineConfig(
    @ConfigName("user.display_max") val userDisplayMax: Max,
    @ConfigName("user.actor.name") val userActorName: String,
)

@Module
final class Env(
    appConfig: Configuration,
    db: lila.db.Db,
    relationApi: lila.relation.RelationApi,
    cacheApi: lila.memo.CacheApi,
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: ActorSystem,
) {

  private val config = appConfig.get[TimelineConfig]("timeline")(AutoConfig.loader)

  lazy val entryApi = new EntryApi(
    coll = db(CollName("timeline_entry")),
    cacheApi = cacheApi,
    userMax = config.userDisplayMax,
  )

  lazy val unsubApi = new UnsubApi(db(CollName("timeline_unsub")))

  def status(channel: String)(userId: String): Fu[Option[Boolean]] =
    unsubApi.get(channel, userId) flatMap {
      case true => fuccess(Some(true)) // unsubed
      case false =>
        entryApi.channelUserIdRecentExists(channel, userId) map {
          case true  => Some(false) // subed
          case false => None        // not applicable
        }
    }

  system.actorOf(Props(wire[Push]), name = config.userActorName)

  lila.common.Bus.subscribeFun("shadowban") { case lila.hub.actorApi.mod.Shadowban(userId, true) =>
    entryApi.removeRecentFollowsBy(userId).unit
  }

  def cli =
    new lila.common.Cli {
      def process = { case "broadcast" :: msgWords =>
        entryApi.broadcast.insert(
          lila.hub.actorApi.timeline.SystemNotification(msgWords mkString " "),
        ) inject "done!"
      }
    }
}
