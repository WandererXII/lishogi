package lila.security

import scala.concurrent.duration._

import lila.common.config.Secret

private object LoginToken {

  def makeTokener(secret: Secret, lifetime: FiniteDuration)(implicit
      ec: scala.concurrent.ExecutionContext,
  ) =
    new StringToken(
      secret = secret,
      getCurrentValue = _ => fuccess("login"),
      lifetime = lifetime.some,
    )
}
