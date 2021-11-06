package lishogi.push

import akka.actor._
import com.google.auth.oauth2.{ GoogleCredentials, ServiceAccountCredentials }
import com.softwaremill.macwire._
import io.methvin.play.autoconfig._
import play.api.Configuration
import play.api.libs.ws.WSClient
import scala.jdk.CollectionConverters._

import lishogi.common.config._
import FirebasePush.configLoader

@Module
final private class PushConfig(
    @ConfigName("collection.device") val deviceColl: CollName,
    @ConfigName("collection.subscription") val subscriptionColl: CollName,
    val web: WebPush.Config,
    val onesignal: OneSignalPush.Config,
    val firebase: FirebasePush.Config
)

final class Env(
    appConfig: Configuration,
    ws: WSClient,
    db: lishogi.db.Db,
    userRepo: lishogi.user.UserRepo,
    getLightUser: lishogi.common.LightUser.Getter,
    proxyRepo: lishogi.round.GameProxyRepo
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: ActorSystem
) {

  private val config = appConfig.get[PushConfig]("push")(AutoConfig.loader)

  def vapidPublicKey = config.web.vapidPublicKey

  private lazy val deviceApi  = new DeviceApi(db(config.deviceColl))
  lazy val webSubscriptionApi = new WebSubscriptionApi(db(config.subscriptionColl))

  def registerDevice    = deviceApi.register _
  def unregisterDevices = deviceApi.unregister _

  private lazy val oneSignalPush = wire[OneSignalPush]

  private lazy val googleCredentials: Option[GoogleCredentials] =
    try {
      config.firebase.json.value.some.filter(_.nonEmpty).map { json =>
        ServiceAccountCredentials
          .fromStream(new java.io.ByteArrayInputStream(json.getBytes()))
          .createScoped(Set("https://www.googleapis.com/auth/firebase.messaging").asJava)
      }
    } catch {
      case e: Exception =>
        logger.warn("Failed to create google credentials", e)
        none
    }
  if (googleCredentials.isDefined) logger.info("Firebase push notifications are enabled.")

  private lazy val firebasePush = wire[FirebasePush]

  private lazy val webPush = wire[WebPush]

  private lazy val pushApi: PushApi = wire[PushApi]

  lishogi.common.Bus.subscribeFun(
    "finishGame",
    "moveEventCorres",
    "newMessage",
    "msgUnread",
    "challenge",
    "corresAlarm",
    "offerEventCorres"
  ) {
    case lishogi.game.actorApi.FinishGame(game, _, _) => pushApi finish game logFailure logger
    case lishogi.hub.actorApi.round.CorresMoveEvent(move, _, pushable, _, _) if pushable =>
      pushApi move move logFailure logger
    case lishogi.hub.actorApi.round.CorresTakebackOfferEvent(gameId) =>
      pushApi takebackOffer gameId logFailure logger
    case lishogi.hub.actorApi.round.CorresDrawOfferEvent(gameId) => pushApi drawOffer gameId logFailure logger
    case lishogi.msg.MsgThread.Unread(t)                         => pushApi newMsg t logFailure logger
    case lishogi.challenge.Event.Create(c)                       => pushApi challengeCreate c logFailure logger
    case lishogi.challenge.Event.Accept(c, joinerId)             => pushApi.challengeAccept(c, joinerId) logFailure logger
    case lishogi.game.actorApi.CorresAlarmEvent(pov)             => pushApi corresAlarm pov logFailure logger
  }
}
