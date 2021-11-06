package controllers

import play.api.mvc._

import lishogi.api.Context
import lishogi.app._
import views._

final class Pref(env: Env) extends LishogiController(env) {

  private def api   = env.pref.api
  private def forms = lishogi.pref.DataForm

  def apiGet =
    Scoped(_.Preference.Read) { _ => me =>
      env.pref.api.getPref(me) map { prefs =>
        JsonOk {
          import play.api.libs.json._
          import lishogi.pref.JsonView._
          Json.obj("prefs" -> prefs)
        }
      }
    }

  def form(categSlug: String) =
    Auth { implicit ctx => me =>
      lishogi.pref.PrefCateg(categSlug) match {
        case None => notFound
        case Some(categ) =>
          Ok(html.account.pref(me, forms prefOf ctx.pref, categ)).fuccess
      }
    }

  def formApply =
    AuthBody { implicit ctx => _ =>
      def onSuccess(data: lishogi.pref.DataForm.PrefData) = api.setPref(data(ctx.pref)) inject Ok("saved")
      implicit val req                                 = ctx.body
      forms.pref
        .bindFromRequest()
        .fold(
          _ =>
            forms.pref
              .bindFromRequest(lishogi.pref.FormCompatLayer(ctx.pref, ctx.body))
              .fold(
                err => BadRequest(err.toString).fuccess,
                onSuccess
              ),
          onSuccess
        )
    }

  def set(name: String) =
    OpenBody { implicit ctx =>
      if (name == "zoom") {
        Ok.withCookies(env.lishogiCookie.session("zoom2", (getInt("v") | 185).toString)).fuccess
      } else {
        implicit val req = ctx.body
        (setters get name) ?? { case (form, fn) =>
          FormResult(form) { v =>
            fn(v, ctx) map { cookie =>
              Ok(()).withCookies(cookie)
            }
          }
        }
      }
    }

  def verifyTitle =
    AuthBody { implicit ctx => me =>
      import play.api.data._, Forms._
      implicit val req = ctx.body
      Form(single("v" -> boolean))
        .bindFromRequest()
        .fold(
          _ => fuccess(Redirect(routes.User.show(me.username))),
          v =>
            api.saveTag(me, _.verifyTitle, if (v) "1" else "0") inject Redirect {
              if (v) routes.Page.notSupported() else routes.User.show(me.username) // master
            }
        )
    }

  private lazy val setters = Map(
    "theme"         -> (forms.theme         -> save("theme") _),
    "pieceSet"      -> (forms.pieceSet      -> save("pieceSet") _),
    "theme3d"       -> (forms.theme3d       -> save("theme3d") _),
    "pieceSet3d"    -> (forms.pieceSet3d    -> save("pieceSet3d") _),
    "soundSet"      -> (forms.soundSet      -> save("soundSet") _),
    "bg"            -> (forms.bg            -> save("bg") _),
    "bgImg"         -> (forms.bgImg         -> save("bgImg") _),
    "is3d"          -> (forms.is3d          -> save("is3d") _),
    "zen"           -> (forms.zen           -> save("zen") _),
    "pieceNotation" -> (forms.pieceNotation -> save("pieceNotation") _)
  )

  private def save(name: String)(value: String, ctx: Context): Fu[Cookie] =
    ctx.me ?? {
      api.setPrefString(_, name, value)
    } inject env.lishogiCookie.session(name, value)(ctx.req)
}
