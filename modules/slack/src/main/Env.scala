package lishogi.slack

import com.softwaremill.macwire._
import play.api.{ Configuration, Mode }
import play.api.libs.ws.WSClient

import lishogi.common.Lishogikka
import lishogi.common.config._
import lishogi.hub.actorApi.plan.ChargeEvent
import lishogi.hub.actorApi.slack.Event
import lishogi.hub.actorApi.user.Note

@Module
final class Env(
    appConfig: Configuration,
    getLightUser: lishogi.common.LightUser.Getter,
    noteApi: lishogi.user.NoteApi,
    ws: WSClient,
    shutdown: akka.actor.CoordinatedShutdown,
    mode: Mode
)(implicit ec: scala.concurrent.ExecutionContext) {

  private val incomingUrl = appConfig.get[Secret]("slack.incoming.url")

  private lazy val client = wire[SlackClient]

  lazy val api: SlackApi = wire[SlackApi]

  if (mode == Mode.Prod) {
    api.publishInfo("Lishogi has started!")
    Lishogikka.shutdown(shutdown, _.PhaseBeforeServiceUnbind, "Tell slack")(api.stop _)
  }

  lishogi.common.Bus.subscribeFun("slack", "plan", "userNote") {
    case d: ChargeEvent                                => api charge d
    case Note(from, to, text, true) if from != "Irwin" => api.userModNote(from, to, text)
    case e: Event                                      => api publishEvent e
  }
}
