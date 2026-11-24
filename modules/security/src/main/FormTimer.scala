package lila.security

import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import scala.concurrent.duration._

import com.roundeights.hasher.Algo

import lila.common.config.Secret

/** Signs the time at which a form was rendered, so that a form submitted faster than a human could
  * fill it can be refused.
  */
final class FormTimer(secret: Secret) {

  import FormTimer._

  private val signer = Algo hmac secret.value

  def make: String = make(nowMillis)

  // Keeps a genuine timestamp when the form is rendered again with errors
  def renew(token: Option[String]): String =
    token.filter(t => Set[Result](Ok, TooFast) contains check(t)) | make

  def check(token: String): Result = check(token, nowMillis)

  private[security] def make(millis: Long): String = s"$millis$separator${sign(millis.toString)}"

  private[security] def check(token: String, now: Long): Result =
    token.split(separator) match {
      case Array(millisStr, signature) if isSigned(millisStr, signature) =>
        millisStr.toLongOption.fold[Result](Invalid) { millis =>
          val age = now - millis
          if (age < minAge.toMillis) TooFast
          else if (age > maxAge.toMillis) Expired
          else Ok
        }
      case _ => Invalid
    }

  private def isSigned(msg: String, signature: String) =
    MessageDigest.isEqual(sign(msg) getBytes UTF_8, signature getBytes UTF_8)

  private def sign(msg: String): String = signer.sha256(msg).hex
}

object FormTimer {

  val minAge = 3 seconds
  val maxAge = 1 hour

  sealed trait Result
  case object Ok      extends Result
  case object TooFast extends Result
  case object Expired extends Result
  case object Invalid extends Result

  private val separator = ':'
}
