package controllers

import scala.concurrent.duration._

import play.api.mvc._
import views._

import lila.api.Context
import lila.app._
import lila.common.EmailAddress
import lila.plan.MonthlyCustomerInfo
import lila.plan.OneTimeCustomerInfo
import lila.plan.StripeCustomer
import lila.user.{ User => UserModel }

final class Plan(env: Env)(implicit system: akka.actor.ActorSystem) extends LilaController(env) {

  private val logger = lila.log("plan")

  def index =
    Open { implicit ctx =>
      pageHit
      ctx.me.fold(indexAnon) { me =>
        import lila.plan.PlanApi.SyncResult._
        env.plan.api.sync(me) flatMap {
          case ReloadUser => Redirect(routes.Plan.index).fuccess
          case Synced(Some(patron), None) =>
            env.user.repo email me.id flatMap { email =>
              renderIndex(email, patron.some)
            }
          case Synced(Some(patron), Some(customer)) => indexPatron(me, patron, customer)
          case Synced(_, _)                         => indexFreeUser(me)
        }
      }
    }

  def list =
    Open { implicit ctx =>
      ctx.me.fold(Redirect(routes.Plan.index).fuccess) { me =>
        import lila.plan.PlanApi.SyncResult._
        env.plan.api.sync(me) flatMap {
          case ReloadUser         => Redirect(routes.Plan.list).fuccess
          case Synced(Some(_), _) => indexFreeUser(me)
          case _                  => Redirect(routes.Plan.index).fuccess
        }
      }
    }

  private def indexAnon(implicit ctx: Context) = renderIndex(email = none, patron = none)

  private def indexFreeUser(me: UserModel)(implicit ctx: Context) =
    env.user.repo email me.id flatMap { email =>
      renderIndex(email, patron = none)
    }

  private def renderIndex(email: Option[EmailAddress], patron: Option[lila.plan.Patron])(implicit
      ctx: Context,
  ): Fu[Result] =
    for {
      recentIds <- env.plan.api.recentChargeUserIds
      bestIds   <- env.plan.api.topPatronUserIds
      _         <- env.user.lightUserApi preloadMany { recentIds ::: bestIds }
    } yield Ok(
      html.plan.index(
        email = email,
        patron = patron,
        recentIds = recentIds,
        bestIds = bestIds,
      ),
    )

  private def indexPatron(me: UserModel, patron: lila.plan.Patron, customer: StripeCustomer)(
      implicit ctx: Context,
  ) =
    env.plan.api.customerInfo(me, customer) flatMap {
      case Some(info: MonthlyCustomerInfo) => Ok(html.plan.indexStripe(me, patron, info)).fuccess
      case Some(info: OneTimeCustomerInfo) =>
        renderIndex(info.customer.email map EmailAddress.apply, patron.some)
      case None =>
        env.user.repo email me.id flatMap { email =>
          renderIndex(email, patron.some)
        }
    }

  def features =
    Open { implicit ctx =>
      pageHit
      fuccess {
        html.plan.features()
      }
    }

  def switch =
    AuthBody { implicit ctx => me =>
      implicit val req = ctx.body
      lila.plan.Switch.form
        .bindFromRequest()
        .fold(
          _ => funit,
          data => env.plan.api.switch(me, data.cents),
        ) inject Redirect(routes.Plan.index)
    }

  def cancel =
    AuthBody { _ => me =>
      env.plan.api.cancel(me) inject Redirect(routes.Plan.index)
    }

  def thanks =
    Open { implicit ctx =>
      // wait for the payment data from stripe or paypal
      lila.common.Future.delay(2.seconds) {
        ctx.me ?? env.plan.api.userPatron flatMap { patron =>
          patron ?? env.plan.api.patronCustomer map { customer =>
            Ok(html.plan.thanks(patron, customer))
          }
        }
      }
    }

  def webhook =
    Action.async(parse.json) { req =>
      env.plan.webhook(req.body) map { _ =>
        Ok("kthxbye")
      }
    }

  def payPalIpn =
    Action.async { implicit req =>
      import lila.plan.Patron.PayPal
      lila.plan.DataForm.ipn
        .bindFromRequest()
        .fold(
          err => {
            if (err.errors("txn_type").nonEmpty) {
              logger.debug(s"Plan.payPalIpn ignore txn_type = ${err.data get "txn_type"}")
              fuccess(Ok)
            } else {
              logger.error(s"Plan.payPalIpn invalid data ${err.toString}")
              fuccess(BadRequest)
            }
          },
          ipn =>
            env.plan.api.onPaypalCharge(
              userId = ipn.userId,
              email = ipn.email map PayPal.Email.apply,
              subId = ipn.subId map PayPal.SubId.apply,
              cents = lila.plan.Cents(ipn.grossCents),
              name = ipn.name,
              txnId = ipn.txnId,
              ip = lila.common.HTTPRequest.lastRemoteAddress(req).value,
              key = get("key", req) | "N/A",
            ) inject Ok,
        )
    }
}
