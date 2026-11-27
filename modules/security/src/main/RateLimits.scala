package lila.security

import scala.concurrent.duration._

import play.api.mvc.RequestHeader

import lila.common.EmailAddress
import lila.common.HTTPRequest
import lila.common.IpAddress
import lila.common.config.NetConfig
import lila.memo.RateLimit
import lila.user.User

// All the rate limits that guard accounts and credentials.
//
// When limits are combined, IP goes first: a request blocked by IP must not use up the credits
// of the targeted user or email.
final class RateLimits(net: NetConfig) {

  private val enforce = net.rateLimit.value

  private val passwordHashPerIP = new RateLimit[IpAddress](
    credits = 30 * 2, // double cost in case of hash check failure
    duration = 8 minutes,
    key = "password.hashes.ip",
    enforce = enforce,
  )

  // Only spends the credits of that IP, so it can go before the IP limit
  private val passwordHashPerUserAndIP = new RateLimit[(String, IpAddress)](
    credits = 5,
    duration = 1 hour,
    key = "password.hashes.user.ip",
    enforce = enforce,
  )

  // more than per user and IP, so that one IP can't lock the owner out
  private val passwordHashPerUser = new RateLimit[String](
    credits = 20,
    duration = 1 hour,
    key = "password.hashes.user",
    enforce = enforce,
  )

  private val ownPasswordHashPerUser = new RateLimit[User.ID](
    credits = 10,
    duration = 1 hour,
    key = "password.hashes.own.user",
    enforce = enforce,
  )

  private val passwordHashGlobal = new RateLimit[String](
    credits = 4 * 10 * 60, // max out 4 cores for 60 seconds
    duration = 1 minute,
    key = "password.hashes.global",
    enforce = enforce,
  )

  // For visitors who hash or check a password: login, signup.
  // The charge is an extra cost for the IP.
  def passwordHash[A](usernameOrEmail: String, req: RequestHeader)(
      run: RateLimit.Charge => Fu[A],
  )(default: => Fu[A]): Fu[A] = {
    val key = EmailAddress(usernameOrEmail).normalize.value
    passwordHashPerUserAndIP(key -> HTTPRequest.lastRemoteAddress(req)) {
      passwordHashBy(passwordHashPerUser, key, req)(run)(default)
    }(default)
  }

  // For users already identified by a session or an emailed token.
  // Visitors can't use up these credits by failing to log in as them.
  def ownPasswordHash[A](userId: User.ID, req: RequestHeader)(
      run: RateLimit.Charge => Fu[A],
  )(default: => Fu[A]): Fu[A] =
    passwordHashBy(ownPasswordHashPerUser, userId, req)(run)(default)

  private def passwordHashBy[A](perUser: RateLimit[String], key: String, req: RequestHeader)(
      run: RateLimit.Charge => Fu[A],
  )(default: => Fu[A]): Fu[A] = {
    val ip = HTTPRequest lastRemoteAddress req
    passwordHashPerIP.chargeable(ip) { charge =>
      perUser(key) {
        passwordHashGlobal("-", msg = ip.value) {
          run(charge)
        }(default)
      }(default)
    }(default)
  }

  private val signupPerIP = RateLimit.composite[IpAddress](
    key = "account.create.ip",
    enforce = enforce,
  )(
    ("fast", 10, 10.minutes),
    ("slow", 60, 1.day),
  )

  def signup[A](req: RequestHeader, cost: Int)(run: => Fu[A])(default: => Fu[A]): Fu[A] =
    signupPerIP(HTTPRequest lastRemoteAddress req, cost = cost)(run)(default)

  private val emailFormPerIP = RateLimit.composite[IpAddress](
    key = "account.email.form.ip",
    enforce = enforce,
  )(
    ("fast", 30, 10.minutes),
    ("slow", 120, 1.day),
  )

  // For forms whose response tells visitors whether an email is already registered.
  // Charge every submission before looking at it
  def emailForm[A](req: RequestHeader)(run: => Fu[A])(default: => Fu[A]): Fu[A] =
    emailFormPerIP(HTTPRequest lastRemoteAddress req)(run)(default)

  // always enforced, like all limits on outgoing emails
  private val emailConfirmPerIP = new RateLimit[IpAddress](
    credits = 40,
    duration = 1 hour,
    key = "email.confirms.ip",
  )

  private val emailConfirmPerUser = new RateLimit[String](
    credits = 3,
    duration = 1 hour,
    key = "email.confirms.user",
  )

  private val emailConfirmPerEmail = new RateLimit[String](
    credits = 3,
    duration = 1 hour,
    key = "email.confirms.email",
  )

  // For known users who want a link to confirm their email: fixing it after signup, or changing it
  def emailConfirm[A](userEmail: EmailConfirm.UserEmail, req: RequestHeader)(
      run: => Fu[A],
  )(default: => Fu[A]): Fu[A] =
    emailConfirmPerIP(HTTPRequest lastRemoteAddress req) {
      emailConfirmPerUser(userEmail.username) {
        emailConfirmPerEmail(userEmail.email.normalize.value) {
          run
        }(default)
      }(default)
    }(default)

  private val emailConfirmHelpPerIP = new RateLimit[IpAddress](
    credits = 10,
    duration = 10 minutes,
    key = "email.confirm.help.ip",
    enforce = enforce,
  )

  def emailConfirmHelp[A](req: RequestHeader)(run: => Fu[A])(default: => Fu[A]): Fu[A] =
    emailConfirmHelpPerIP(HTTPRequest lastRemoteAddress req)(run)(default)

  private val accountRecoveryPerIP = new RateLimit[IpAddress](
    credits = 10,
    duration = 1 hour,
    key = "account.recovery.ip",
  )

  private val accountRecoveryPerIPAndEmail = new RateLimit[(IpAddress, String)](
    credits = 3,
    duration = 1 hour,
    key = "account.recovery.ip.email",
  )

  private val accountRecoveryPerEmail = new RateLimit[String](
    credits = 6, // more than per IP and email, so that one IP can't lock the owner out
    duration = 1 hour,
    key = "account.recovery.email",
  )

  // Shared by all the forms that let an unauthenticated visitor into an account,
  // by sending a link to its email: magic link, password reset, reopen.
  // Keyed by the submitted email, and charged whether or not it matches an account,
  // so that being rate limited doesn't reveal which emails are registered.
  // No limit per user: a link is only sent to the account's own email, so the email limit covers it.
  def accountRecovery[A](email: EmailAddress, req: RequestHeader)(
      run: => Fu[A],
  )(default: => Fu[A]): Fu[A] = {
    val ip         = HTTPRequest lastRemoteAddress req
    val normalized = email.normalize.value
    accountRecoveryPerIP(ip) {
      accountRecoveryPerIPAndEmail(ip -> normalized) {
        accountRecoveryPerEmail(normalized) {
          run
        }(default)
      }(default)
    }(default)
  }

  private val oAuthTokenPerIP = new RateLimit[IpAddress](
    credits = 60,
    duration = 10 minutes,
    key = "oauth.token.ip",
    enforce = enforce,
  )

  // When an app exchanges an authorization code for a token
  def oAuthToken[A](req: RequestHeader)(run: => Fu[A])(default: => Fu[A]): Fu[A] =
    oAuthTokenPerIP(HTTPRequest lastRemoteAddress req)(run)(default)

  private val personalTokenPerUser = new RateLimit[User.ID](
    credits = 20,
    duration = 1 day,
    key = "oauth.token.create.user",
    enforce = enforce,
  )

  // When a user creates a personal access token
  def personalToken[A](user: User)(run: => Fu[A])(default: => Fu[A]): Fu[A] =
    personalTokenPerUser(user.id)(run)(default)
}
