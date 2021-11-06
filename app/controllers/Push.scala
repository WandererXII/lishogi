package controllers

import lishogi.app._
import lishogi.push.WebSubscription
import lishogi.push.WebSubscription.readers._

final class Push(env: Env) extends LishogiController(env) {

  def mobileRegister(platform: String, deviceId: String) =
    Auth { implicit ctx => me =>
      env.push.registerDevice(me, platform, deviceId)
    }

  def mobileUnregister =
    Auth { implicit ctx => me =>
      env.push.unregisterDevices(me)
    }

  def webSubscribe =
    AuthBody(parse.json) { implicit ctx => me =>
      val currentSessionId = ~env.security.api.reqSessionId(ctx.req)
      ctx.body.body
        .validate[WebSubscription]
        .fold(
          err => BadRequest(err.toString).fuccess,
          data => env.push.webSubscriptionApi.subscribe(me, data, currentSessionId) inject NoContent
        )
    }
}
