package lila.security

import scala.concurrent.duration._

import play.api.libs.json._
import play.api.libs.ws.WSClient

import reactivemongo.api.bson.BSONHandler

import lila.base.LilaException
import lila.common.Domain
import lila.db.dsl._

final private class DnsApi(
    ws: WSClient,
    config: SecurityConfig.DnsApi,
    mongoCache: lila.memo.MongoCache.Api,
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: akka.actor.ActorSystem,
) {

  // only valid email domains that are not whitelisted should make it here
  def mx(domain: Domain.Lower): Fu[List[Domain]] =
    failsafe(domain, List(domain.domain)) {
      mxCache get domain
    }

  implicit private val DomainBSONHandler: BSONHandler[Domain] =
    stringAnyValHandler[Domain](_.value, Domain.apply)

  private val mxCache = mongoCache.only[Domain.Lower, List[Domain]](
    "security.mx",
    7 days,
    _.value,
  ) { domain =>
    fetch(domain, "mx") {
      _ take 20 flatMap { obj =>
        (obj \ "data").asOpt[String].map(_ split ' ') collect { case Array(_, domain) =>
          Domain {
            if (domain endsWith ".") domain.dropRight(1)
            else domain
          }
        }
      }
    }.monSuccess(_.security.dnsApi.mx)
  }

  private def fetch[A](domain: Domain.Lower, tpe: String)(f: List[JsObject] => A): Fu[A] =
    ws.url(config.url)
      .withQueryStringParameters("name" -> domain.value, "type" -> tpe)
      .withHttpHeaders("Accept" -> "application/dns-json")
      .get() withTimeout config.timeout map {
      case res if res.status == 200 || res.status == 404 =>
        f(~(res.json \ "Answer").asOpt[List[JsObject]])
      case res => throw LilaException(s"Status ${res.status}")
    }

  // if the DNS service fails, assume the best
  private def failsafe[A](domain: Domain.Lower, default: => A)(f: => Fu[A]): Fu[A] =
    f recover { case e: Exception =>
      logger.warn(s"DnsApi $domain", e)
      default
    }
}
