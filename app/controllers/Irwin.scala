package controllers

import lishogi.app._

final class Irwin(env: Env) extends LishogiController(env) {

  import lishogi.irwin.JSONHandlers.reportReader

  def dashboard =
    Secure(_.SeeReport) { implicit ctx => _ =>
      env.irwin.api.dashboard map { d =>
        Ok(views.html.irwin.dashboard(d))
      }
    }

  def saveReport =
    ScopedBody(parse.json)(Nil) { req => me =>
      isGranted(_.Admin, me) ?? {
        req.body
          .validate[lishogi.irwin.IrwinReport]
          .fold(
            err => fuccess(BadRequest(err.toString)),
            report => env.irwin.api.reports.insert(report) inject Ok
          ) map (_ as TEXT)
      }
    }

  def eventStream =
    Scoped() { _ => me =>
      isGranted(_.Admin, me) ?? {
        noProxyBuffer(Ok.chunked(env.irwin.stream())).fuccess
      }
    }
}
