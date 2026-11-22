package controllers

import scala.concurrent.duration._

import views._

import lila.app._
import lila.oauth.AccessToken
import lila.oauth.OAuthTokenForm

final class OAuthToken(env: Env) extends LilaController(env) {

  private val tokenApi = env.oAuth.tokenApi

  private val CreateRateLimitPerUser = new lila.memo.RateLimit[lila.user.User.ID](
    credits = 20,
    duration = 1.day,
    key = "oauth.token.create.user",
    enforce = env.net.rateLimit.value,
  )

  def index =
    Auth { implicit ctx => me =>
      tokenApi.listPersonal(me) map { tokens =>
        Ok(html.oAuth.token.index(tokens))
      }
    }

  def create =
    Auth { implicit ctx => me =>
      val form = OAuthTokenForm.create fill OAuthTokenForm.Data(
        description = ~get("description"),
        scopes = (~ctx.req.queryString.get("scopes[]")).toList,
      )
      Ok(html.oAuth.token.create(form, me)).fuccess
    }

  def createApply =
    AuthBody { implicit ctx => me =>
      implicit val req = ctx.body
      OAuthTokenForm.create
        .bindFromRequest()
        .fold(
          err => BadRequest(html.oAuth.token.create(err, me)).fuccess,
          setup =>
            CreateRateLimitPerUser(me.id) {
              tokenApi.create(setup, me) inject
                Redirect(routes.OAuthToken.index).flashSuccess
            }(rateLimitedFu),
        )
    }

  def delete(id: String) =
    Auth { _ => me =>
      tokenApi.revokeById(AccessToken.Id(id), me) inject Redirect(
        routes.OAuthToken.index,
      ).flashSuccess
    }
}
