package lila.security

import play.api.mvc.RequestHeader

import org.joda.time.DateTime
import reactivemongo.api.ReadPreference

import lila.common.IpAddress
import lila.db.dsl._

final class Firewall(
    coll: Coll,
)(implicit ec: scala.concurrent.ExecutionContext) {

  @volatile private var current: Set[String] = Set.empty

  loadFromDb.logFailure(logger, e => s"Firewall.loadFromDb: $e").unit

  def blocksIp(ip: IpAddress): Boolean = current contains ip.value

  def blocks(req: RequestHeader): Boolean = {
    val v = blocksIp {
      lila.common.HTTPRequest lastRemoteAddress req
    }
    if (v) lila.mon.security.firewall.block.increment()
    v
  }

  def accepts(req: RequestHeader): Boolean = !blocks(req)

  def blockIps(ips: Iterable[IpAddress]): Funit =
    ips.map { ip =>
      validIp(ip) ?? {
        coll.update
          .one(
            $id(ip),
            $doc("_id" -> ip, "date" -> DateTime.now),
            upsert = true,
          )
          .void
      }
    }.sequenceFu >> loadFromDb

  def unblockIps(ips: Iterable[IpAddress]): Funit =
    coll.delete.one($inIds(ips.filter(validIp))).void >>- loadFromDb.unit

  private def loadFromDb: Funit =
    coll.distinctEasy[String, Set]("_id", $empty, ReadPreference.secondaryPreferred).map { ips =>
      current = ips
      lila.mon.security.firewall.ip.update(ips.size).unit
    }

  private def validIp(ip: IpAddress) =
    (IpAddress.isv4(ip) && ip.value != "127.0.0.1" && ip.value != "0.0.0.0") ||
      (IpAddress.isv6(ip) && ip.value != "0:0:0:0:0:0:0:1" && ip.value != "0:0:0:0:0:0:0:0")
}
