package lishogi.api

import play.api.libs.ws.WSClient

final class InfluxEvent(
    ws: WSClient,
    endpoint: String,
    env: String
)(implicit ec: scala.concurrent.ExecutionContext) {

  private val seed = lishogi.common.ThreadLocalRandom.nextString(6)

  def start() = apply("lishogi_start", s"Lishogi starts: $seed")

  def friendListToggle(value: Boolean) = apply(s"friend_list_$value", s"Toggle friend list: $value")

  private def apply(key: String, text: String) =
    ws.url(endpoint)
      .post(s"""event,program=lishogi,env=$env,title=$key text="$text"""")
      .effectFold(
        err => lishogi.log("influxEvent").error(endpoint, err),
        res => if (res.status != 204) lishogi.log("influxEvent").error(s"$endpoint ${res.status}")
      )
}
