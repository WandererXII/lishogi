package lila.security

import scala.util.chaining._

import play.api.data._
import play.api.http.HeaderNames
import play.api.i18n.Lang
import play.api.mvc.Request
import play.api.mvc.RequestHeader

import lila.common.EmailAddress
import lila.common.HTTPRequest
import lila.user.User

final class Signup(
    store: Store,
    api: SecurityApi,
    ipTrust: IpTrust,
    forms: SecurityForm,
    emailAddressValidator: EmailAddressValidator,
    emailConfirm: EmailConfirm,
    formTimer: FormTimer,
    authenticator: lila.user.Authenticator,
    userRepo: lila.user.UserRepo,
    rateLimits: RateLimits,
)(implicit ec: scala.concurrent.ExecutionContext) {

  sealed abstract private class MustConfirmEmail(val value: Boolean)
  private object MustConfirmEmail {

    case object Nope                  extends MustConfirmEmail(false)
    case object YesBecausePrintExists extends MustConfirmEmail(true)
    case object YesBecauseIpExists    extends MustConfirmEmail(true)
    case object YesBecauseIpSusp      extends MustConfirmEmail(true)
    case object YesBecauseUA          extends MustConfirmEmail(true)

    def apply(signals: Signup.Signals): MustConfirmEmail =
      if (signals.ipExists) YesBecauseIpExists
      else if (signals.weirdUA) YesBecauseUA
      else if (signals.printExists) YesBecausePrintExists
      else if (signals.ipSusp) YesBecauseIpSusp
      else Nope
  }

  private def getSignals(
      print: Option[FingerPrint],
  )(implicit req: RequestHeader): Fu[Signup.Signals] = {
    val ip = HTTPRequest lastRemoteAddress req
    store.recentByIpExists(ip) zip print.fold(fuFalse)(store.recentByPrintExists) map {
      case (ipExists, printExists) =>
        Signup.Signals(
          ipExists = ipExists,
          ipSusp = ipTrust.isSuspicious(ip),
          printExists = printExists,
          printMissing = print.flatMap(_.hash).isEmpty,
          weirdUA = HTTPRequest weirdUA req,
          langMissing = !req.headers.hasHeader(HeaderNames.ACCEPT_LANGUAGE),
        )
    }
  }

  def website(
      blind: Boolean,
  )(implicit req: Request[_], lang: Lang, formBinding: FormBinding): Fu[Signup.Result] =
    rateLimits.emailForm(req) {
      forms.preloadEmailDns >> bindWebsite(blind)
    }(rateLimitDefault)

  private def bindWebsite(
      blind: Boolean,
  )(implicit req: Request[_], lang: Lang, formBinding: FormBinding): Fu[Signup.Result] = {
    val form = forms.signup.website.bindFromRequest()
    form
      .fold[Fu[Signup.Result]](
        err => fuccess(Signup.Bad(err tap signupErrLog)),
        data =>
          Signup.trap(data, formTimer) match {
            case Some(trap) =>
              authLog(data.username, data.email, s"Signup trap: $trap")
              rateLimits.signup(req, trap.cost) {
                fuccess[Signup.Result](Signup.Bad(form withGlobalError trap.message))
              }(rateLimitDefault)
            case None =>
              getSignals(data.fingerPrint) flatMap { signals =>
                signupRateLimit(data.username, signals.cost) {
                  val mustConfirm = MustConfirmEmail(signals)
                  lila.mon.user.register.count.increment()
                  lila.mon.user.register.mustConfirmEmail(mustConfirm.toString).increment()
                  val email = emailAddressValidator
                    .validate(data.realEmail) err s"Invalid email ${data.email}"
                  val passwordHash = authenticator passEnc User.ClearPassword(data.password)
                  userRepo
                    .create(
                      data.username,
                      passwordHash,
                      email.acceptable,
                      blind,
                      mustConfirmEmail = mustConfirm.value,
                    )
                    .flatMap {
                      case None => // created since the form was validated, i.e. double submit
                        authLog(data.username, data.email, "Signup username just taken")
                        fuccess(Signup.Bad(form.withError("username", "usernameAlreadyUsed")))
                      case Some(user) =>
                        logSignup(user, email.acceptable, data.fingerPrint, mustConfirm)
                        confirmOrAllSet(email, mustConfirm, data.fingerPrint)(user)
                    }
                }
              }
          },
      )
  }

  private def confirmOrAllSet(
      email: EmailAddressValidator.Acceptable,
      mustConfirm: MustConfirmEmail,
      fingerPrint: Option[FingerPrint],
  )(user: User)(implicit req: RequestHeader, lang: Lang): Fu[Signup.Result] =
    store.deletePreviousSessions(user) >> {
      if (mustConfirm.value) {
        emailConfirm.send(user, email.acceptable) >> {
          if (emailConfirm.effective)
            api.saveSignup(user.id, fingerPrint) inject
              Signup.ConfirmEmail(user, email.acceptable)
          else fuccess(Signup.AllSet(user, email.acceptable))
        }
      } else fuccess(Signup.AllSet(user, email.acceptable))
    }

  private val rateLimitDefault = fuccess[Signup.Result](Signup.RateLimited)

  private def signupRateLimit(username: String, cost: Int)(
      f: => Fu[Signup.Result],
  )(implicit req: RequestHeader): Fu[Signup.Result] =
    rateLimits.passwordHash(username, req) { _ =>
      rateLimits.signup(req, cost)(f)(rateLimitDefault)
    }(rateLimitDefault)

  private def logSignup(
      user: User,
      email: EmailAddress,
      fingerPrint: Option[FingerPrint],
      mustConfirm: MustConfirmEmail,
  ) =
    authLog(
      user.username,
      email.value,
      s"fp: ${fingerPrint} mustConfirm: $mustConfirm fp: ${fingerPrint.??(_.value)}}",
    )

  private def signupErrLog(err: Form[_]) =
    for {
      username <- err("username").value
      email    <- err("email").value
    } {
      if (
        err.errors.exists(_.messages.contains("error.email_acceptable")) &&
        err("email").value.exists(EmailAddress.matches)
      )
        authLog(username, email, s"Signup with unacceptable email")
    }

  private def authLog(user: String, email: String, msg: String) =
    lila.log("auth").info(s"$user $email $msg")
}

object Signup {

  private[security] case class Signals(
      ipExists: Boolean,
      ipSusp: Boolean,
      printExists: Boolean,
      printMissing: Boolean,
      weirdUA: Boolean,
      langMissing: Boolean,
  ) {
    // Rate limit credits. A shared IP (school, club, CGNAT) is not suspicious by itself.
    def cost: Int =
      1 + List(
        ipSusp       -> 2,
        printExists  -> 1,
        printMissing -> 2,
        weirdUA      -> 2,
        langMissing  -> 2,
      ).collect { case (true, extra) => extra }.sum
  }

  sealed abstract private[security] class Trap(val message: String, val cost: Int)

  private[security] object Trap {
    case object Honeypot extends Trap("Sign up failed. Please try again.", 5)
    case object TooFast  extends Trap("Please wait a few seconds and try again.", 1)
    case object Expired  extends Trap("This form has expired. Please try again.", 1)
    case object Forged   extends Trap("This form has expired. Please try again.", 3)
  }

  private[security] def trap(data: SecurityForm.SignupData, formTimer: FormTimer): Option[Trap] =
    if (data.honeypot.exists(_.nonEmpty)) Trap.Honeypot.some
    else
      formTimer.check(~data.formTime) match {
        case FormTimer.Ok      => none
        case FormTimer.TooFast => Trap.TooFast.some
        case FormTimer.Expired => Trap.Expired.some
        case FormTimer.Invalid => Trap.Forged.some
      }

  sealed trait Result
  case class Bad(err: Form[_])                             extends Result
  case object RateLimited                                  extends Result
  case class ConfirmEmail(user: User, email: EmailAddress) extends Result
  case class AllSet(user: User, email: EmailAddress)       extends Result
}
