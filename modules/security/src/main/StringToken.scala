package lila.security

import java.nio.charset.StandardCharsets.UTF_8
import scala.concurrent.duration._

import com.roundeights.hasher.Algo
import org.joda.time.DateTime
import org.mindrot.BCrypt

import lila.common.String.base64
import lila.common.config.Secret

// The current value is never in the token, only a short hmac of it
final class StringToken(
    secret: Secret,
    getCurrentValue: String => Fu[String],
    lifetime: Option[FiniteDuration] = none, // never expires if none
    fullHashSize: Int = 14,
    currentValueHashSize: Int = 6,
)(implicit ec: scala.concurrent.ExecutionContext) {

  private val separator     = '|'
  private val dateSeparator = ':'

  def make(payload: String): Fu[String] = makeAt(payload, DateTime.now)

  private[security] def makeAt(payload: String, date: DateTime): Fu[String] =
    hashCurrentValue(payload) map { hash =>
      val value    = if (lifetime.isDefined) s"${date.getMillis}$dateSeparator$hash" else hash
      val signed   = signPayload(payload, value)
      val checksum = makeHash(signed)
      val token    = s"$signed$separator$checksum"
      base64 encodeUrl token
    }

  def read(token: String): Fu[Option[String]] =
    (base64 decode token) ?? {
      _.split(separator).toList match {
        case payloadParts :+ value :+ checksum =>
          val payload = payloadParts mkString separator.toString // may contain the separator
          BCrypt.bytesEqualSecure(
            makeHash(signPayload(payload, value)).getBytes(UTF_8),
            checksum.getBytes(UTF_8),
          ) ?? {
            unexpiredHash(value) ?? { hash =>
              hashCurrentValue(payload) map { current => (hash == current) option payload }
            }
          }
        case _ => fuccess(none)
      }
    }

  private def unexpiredHash(value: String): Option[String] =
    lifetime.fold(value.some) { duration =>
      value split dateSeparator match {
        case Array(millis, hash) =>
          millis.toLongOption.exists(_ > nowMillis - duration.toMillis) option hash
        case _ => none
      }
    }

  private def makeHash(msg: String) = Algo.hmac(secret.value).sha256(msg).hex take fullHashSize

  private def hashCurrentValue(payload: String) =
    getCurrentValue(payload) map { makeHash(_) take currentValueHashSize }

  private def signPayload(payload: String, value: String) =
    s"$payload$separator$value"
}
