package controllers

import play.api.libs.json._

import lila.app._
import lila.common.LightUser.lightUserWrites

final class Msg(
    env: Env,
) extends LilaController(env) {

  def home =
    Auth { implicit ctx => me =>
      ctx.hasInbox ?? negotiate(
        html = inboxJson(me) map { json =>
          Ok(views.html.msg.home(json))
        },
        json = inboxJson(me) map { Ok(_) },
      )
    }

  def convo(username: String, before: Option[Long] = None) =
    Auth { implicit ctx => me =>
      if (username == "new")
        Redirect(get("user").fold(routes.Msg.home)(routes.Msg.convo(_))).fuccess
      else
        ctx.hasInbox ?? env.msg.api.convoWith(me, username, before).flatMap {
          case None =>
            negotiate(
              html = Redirect(routes.Msg.home).fuccess,
              json = notFoundJson(),
            )
          case Some(c) =>
            def newJson = inboxJson(me).map { _ + ("convo" -> env.msg.json.convo(c)) }
            negotiate(
              html = newJson map { json =>
                Ok(views.html.msg.home(json))
              },
              json = newJson map { Ok(_) },
            )
        }
    }

  def search(q: String) =
    Auth { ctx => me =>
      ctx.hasInbox ?? {
        q.trim.some.filter(_.sizeIs > 1).filter(lila.user.User.couldBeUsername) match {
          case None    => env.msg.json.searchResult(me)(env.msg.search.empty) map { Ok(_) }
          case Some(q) => env.msg.search(me, q) flatMap env.msg.json.searchResult(me) map { Ok(_) }
        }
      }
    }

  def unreadCount =
    Auth { ctx => me =>
      JsonOk {
        ctx.hasInbox ?? {
          env.msg.api unreadCount me
        }
      }
    }

  def convoDelete(username: String) =
    Auth { _ => me =>
      env.msg.api.delete(me, username) >>
        inboxJson(me) map { Ok(_) }
    }

  def apiPost(username: String) = {
    val userId = lila.user.User normalize username
    ScopedBody(_.Msg.Write) { implicit req => me =>
      (!me.kid && userId != me.id) ?? {
        import play.api.data._
        import play.api.data.Forms._
        Form(single("text" -> nonEmptyText))
          .bindFromRequest()
          .fold(
            err => jsonFormErrorFor(err, req, me.some),
            text => env.msg.api.post(me.id, userId, text),
          )
      }
    }
  }

  private def inboxJson(me: lila.user.User) =
    env.msg.api.threadsOf(me) flatMap env.msg.json.threads(me) map { threads =>
      Json.obj(
        "me"       -> lightUserWrites.writes(me.light).add("kid" -> me.kid),
        "contacts" -> threads,
      )
    }
}
