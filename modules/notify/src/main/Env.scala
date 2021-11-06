package lishogi.notify

import akka.actor._
import com.softwaremill.macwire._
import io.methvin.play.autoconfig._
import play.api.Configuration

import lishogi.common.Bus
import lishogi.common.config._

private class NotifyConfig(
    @ConfigName("collection.notify") val notifyColl: CollName,
    @ConfigName("actor.name") val actorName: String
)

@Module
final class Env(
    appConfig: Configuration,
    db: lishogi.db.Db,
    userRepo: lishogi.user.UserRepo,
    getLightUser: lishogi.common.LightUser.Getter,
    getLightUserSync: lishogi.common.LightUser.GetterSync,
    cacheApi: lishogi.memo.CacheApi
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: ActorSystem
) {

  private val config = appConfig.get[NotifyConfig]("notify")(AutoConfig.loader)

  lazy val jsonHandlers = wire[JSONHandlers]

  private lazy val repo = new NotificationRepo(coll = db(config.notifyColl))

  private val maxPerPage = MaxPerPage(7)

  lazy val api = wire[NotifyApi]

  // api actor
  Bus.subscribe(
    system.actorOf(
      Props(new Actor {
        def receive = {
          case lishogi.hub.actorApi.notify.Notified(userId) =>
            api markAllRead Notification.Notifies(userId)
          case lishogi.hub.actorApi.notify.NotifiedBatch(userIds) =>
            api markAllRead userIds.map(Notification.Notifies.apply)
          case lishogi.game.actorApi.CorresAlarmEvent(pov) =>
            pov.player.userId ?? { userId =>
              lishogi.game.Namer.playerText(pov.opponent)(getLightUser) foreach { opponent =>
                api addNotification Notification.make(
                  Notification.Notifies(userId),
                  CorresAlarm(
                    gameId = pov.gameId,
                    opponent = opponent
                  )
                )
              }
            }
        }
      }),
      name = config.actorName
    ),
    "corresAlarm"
  )
}
