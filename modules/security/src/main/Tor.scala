package lila.security

import play.api.libs.ws.WSClient

import lila.common.IpAddress

final class Tor(ws: WSClient, config: SecurityConfig.Tor)(implicit
    ec: scala.concurrent.ExecutionContext,
) {

  @volatile private var ips = Set.empty[IpAddress]

  private[security] def refresh: Fu[Set[IpAddress]] =
    ws.url(config.providerUrl).get() flatMap {
      case res if res.status == 200 =>
        ips = res.body.linesIterator.filterNot(_ startsWith "#").flatMap(IpAddress.from).toSet
        lila.mon.security.torNodes.update(ips.size)
        fuccess(ips)
      case res => fufail(s"Tor.refresh ${config.providerUrl} ${res.status}")
    }

  def isExitNode(ip: IpAddress) = ips contains ip
}
