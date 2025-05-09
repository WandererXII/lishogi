package lila.push

import scala.concurrent.Future
import scala.concurrent.blocking
import scala.concurrent.duration._

import play.api.ConfigLoader
import play.api.libs.json._
import play.api.libs.ws.WSClient

import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import io.methvin.play.autoconfig._

import lila.common.Chronometer
import lila.user.User

final private class FirebasePush(
    credentialsOpt: Option[GoogleCredentials],
    deviceApi: DeviceApi,
    ws: WSClient,
    config: FirebasePush.Config,
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: akka.actor.ActorSystem,
) {

  private val workQueue =
    new lila.hub.DuctSequencer(maxSize = 512, timeout = 10 seconds, name = "firebasePush")

  def apply(userId: User.ID, data: => PushApi.Data): Funit =
    credentialsOpt ?? { creds =>
      deviceApi.findLastManyByUserId("firebase", 3)(userId) flatMap {
        case Nil => funit
        // access token has 1h lifetime and is requested only if expired
        case devices =>
          workQueue {
            Future {
              Chronometer.syncMon(_.blocking time "firebase") {
                blocking {
                  creds.refreshIfExpired()
                  creds.getAccessToken()
                }
              }
            }
          }.chronometer.mon(_.push.googleTokenTime).result flatMap { token =>
            // TODO http batch request is possible using a multipart/mixed content
            // unfortuntely it doesn't seem easily doable with play WS
            devices.map(send(token, _, data)).sequenceFu.void
          }
      }
    }

  private def send(token: AccessToken, device: Device, data: => PushApi.Data): Funit =
    ws.url(config.url)
      .withHttpHeaders(
        "Authorization" -> s"Bearer ${token.getTokenValue}",
        "Accept"        -> "application/json",
        "Content-type"  -> "application/json; UTF-8",
      )
      .post(
        Json.obj(
          "message" -> Json.obj(
            "token" -> device._id,
            // firebase doesn't support nested data object and we only use what is
            // inside userData
            "data" -> (data.payload \ "userData").asOpt[JsObject].map(transform(_)),
            "notification" -> Json.obj(
              "body"  -> data.body,
              "title" -> data.title,
            ),
          ),
        ),
      ) flatMap {
      case res if res.status == 200 => funit
      case res if res.status == 404 =>
        logger.info(s"Delete missing firebase device ${device}")
        deviceApi delete device
      case res => fufail(s"[push] firebase: ${res.status}")
    }

  // filter out any non string value, otherwise Firebase API silently rejects
  // the request
  private def transform(obj: JsObject): JsObject =
    JsObject(obj.fields.collect {
      case (k, v: JsString) => s"lishogi.$k" -> v
      case (k, v: JsNumber) => s"lishogi.$k" -> JsString(v.toString)
    })
}

private object FirebasePush {

  final class Config(
      val url: String,
      val json: lila.common.config.Secret,
  )
  implicit val configLoader: ConfigLoader[Config] = AutoConfig.loader[Config]
}
