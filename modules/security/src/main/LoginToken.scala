package lila.security

import scala.concurrent.duration._

import org.joda.time.DateTime

import lila.common.config.Secret
import lila.user.User

private object LoginToken {

  import StringToken.DateStr

  def makeTokener(secret: Secret, lifetime: FiniteDuration)(implicit
      ec: scala.concurrent.ExecutionContext,
  ) =
    new StringToken[User.ID](
      secret = secret,
      getCurrentValue = _ => fuccess(DateStr toStr DateTime.now),
      currentValueHashSize = none,
      valueChecker = StringToken.ValueChecker.Custom(v =>
        fuccess {
          DateStr.toDate(v) exists DateTime.now.minusSeconds(lifetime.toSeconds.toInt).isBefore
        },
      ),
    )
}
