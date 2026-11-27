package lila.security

import play.api.libs.ws.WSClient

import lila.common.Domain

final class DisposableEmailDomain(
    ws: WSClient,
    providerUrl: String,
)(implicit ec: scala.concurrent.ExecutionContext) {

  @volatile private var domains: Set[String] = DisposableEmailDomain.staticBlacklist

  private[security] def refresh(): Unit =
    ws.url(providerUrl).get() map {
      case res if res.status == 200 =>
        val fetched = res.body.linesIterator
          .map(_.trim.toLowerCase)
          .filter(l => l.nonEmpty && !l.startsWith("#"))
          .toSet
        lila.mon.email.disposableDomain.update(fetched.size)
        domains = DisposableEmailDomain.staticBlacklist ++ fetched
      case res =>
        logger.warn(s"DisposableEmailDomain.refresh: status ${res.status}, keeping current list")
    } recover { case e: Exception =>
      logger.warn("DisposableEmailDomain.refresh: keeping current list", e)
    } unit

  // matches the domain itself and any parent suffix (a.b.c -> a.b.c, b.c, c)
  def apply(domain: Domain): Boolean = {
    val lower = domain.lower
    !DisposableEmailDomain.whitelisted(lower) && {
      val current = domains
      lower.value.split('.').tails.exists(parts => parts.nonEmpty && current(parts.mkString(".")))
    }
  }

  def isOk(domain: Domain) = !apply(domain)

}

private object DisposableEmailDomain {

  def whitelisted(domain: Domain.Lower) = whitelist contains domain.value

  private val staticBlacklist = Set(
    "lishogi.org",
    "gamil.com",
    "gmeil.com",
    "gmali.com",
    "gmil.com",
    "gamail.com",
    "gnail.com",
    "hotamil.com",
  )

  private val whitelist = Set(
    /* Default domains included */
    "aol.com",
    "att.net",
    "facebook.com",
    "gmail.com",
    "gmx.com",
    "googlemail.com",
    "google.com",
    "hotmail.com",
    "hotmail.co.uk",
    "ikmail.com",
    "mac.com",
    "me.com",
    "mail.com",
    "msn.com",
    "live.com",
    "live.com.mx",
    "live.com.ar",
    "live.com.au",
    "live.com.br",
    "live.com.my",
    "live.com.pt",
    "live.com.sg",
    "live.com.org",
    "sbcglobal.net",
    "verizon.net",
    "yahoo.com",
    "yahoo.co.uk",
    "protonmail.com",
    "protonmail.ch",
    "pm.me",
    "proton.me",
    /* Other global domains */
    "email.com",
    "games.com" /* AOL */,
    "gmx.net",
    "hush.com",
    "hushmail.com",
    "icloud.com",
    "inbox.com",
    "lavabit.com",
    "love.com" /* AOL */,
    "outlook.com",
    "outlook.com.au",
    "pobox.com",
    "rocketmail.com" /* Yahoo */,
    "safe-mail.net",
    "wow.com" /* AOL */,
    "ygm.com" /* AOL */,
    "ymail.com" /* Yahoo */,
    "zoho.com",
    "zoho.in",
    "zohomail.eu",
    "fastmail.com",
    "fastmail.fm",
    "yandex.com",
    "yandex.net",
    /* United States ISP domains */
    "bellsouth.net",
    "charter.net",
    "comcast.net",
    "cox.net",
    "earthlink.net",
    "juno.com",
    /* Canadian ISP domains */
    "yahoo.ca",
    /* British ISP domains */
    "btinternet.com",
    "virginmedia.com",
    "blueyonder.co.uk",
    "freeserve.co.uk",
    "live.co.uk",
    "ntlworld.com",
    "o2.co.uk",
    "orange.net",
    "sky.com",
    "talktalk.co.uk",
    "tiscali.co.uk",
    "virgin.net",
    "wanadoo.co.uk",
    "bt.com",
    /* Domains used in Asia */
    "sina.com",
    "qq.com",
    "naver.com",
    "hanmail.net",
    "daum.net",
    "nate.com",
    "yahoo.co.jp",
    "outlook.jp",
    "yahoo.co.kr",
    "yahoo.co.id",
    "yahoo.co.in",
    "yahoo.com.sg",
    "yahoo.com.ph",
    /* French ISP domains */
    "hotmail.fr",
    "live.fr",
    "laposte.net",
    "yahoo.fr",
    "wanadoo.fr",
    "orange.fr",
    "gmx.fr",
    "sfr.fr",
    "neuf.fr",
    "free.fr",
    /* German ISP domains */
    "aikq.de",
    "gmx.de",
    "hotmail.de",
    "live.de",
    "online.de",
    "t-online.de" /* T-Mobile */,
    "web.de",
    "yahoo.de",
    /* Russian ISP domains */
    "mail.ru",
    "rambler.ru",
    "yandex.ru",
    "ya.ru",
    "list.ru",
    /* Belgian ISP domains */
    "hotmail.be",
    "live.be",
    "skynet.be",
    "voo.be",
    "tvcablenet.be",
    "telenet.be",
    /* Argentinian ISP domains */
    "hotmail.com.ar",
    "yahoo.com.ar",
    "fibertel.com.ar",
    "speedy.com.ar",
    "arnet.com.ar",
    /* Domains used in Vietnam */
    "outlook.com.vn",
    "yahoo.com.vn",
    "vnnic.vn",
    /* Domains used in Mexico */
    "yahoo.com.mx",
    "hotmail.es",
    "hotmail.com.mx",
    "prodigy.net.mx",
    /* Domains used in Brazil */
    "yahoo.com.br",
    "hotmail.com.br",
    "outlook.com.br",
    "uol.com.br",
    "bol.com.br",
    "terra.com.br",
    "ig.com.br",
    "itelefonica.com.br",
    "r7.com",
    "zipmail.com.br",
    "globo.com",
    "globomail.com",
    "oi.com.br",
    /* Domains used in Portugal */
    "sapo.pt",
    "outlook.pt",
    /* Domains without an A record */
    "cabletv.on.ca",
    "live.ca",
    "unitybox.de",
    "volki.at",
    /* others */
    "seznam.cz",
    "skole.hr",
    "freeshell.org",
    "hotmail.nl",
    "live.nl",
    "startmail.com",
    "palaciodegranda.com",
    "laudepalaciogranda.com",
    "mozmail.com", // Mozilla Firefox Relay Domain
  )
}
