package lila.app
package templating

import play.api.i18n.Lang
import play.api.libs.json.JsValue
import play.api.mvc.RequestHeader

import lila.api.Context
import lila.app.ui.ScalatagsTemplate._
import lila.common.AssetVersion
import lila.common.ContentSecurityPolicy
import lila.common.Nonce
import lila.common.String.html.safeJsonValue

trait AssetHelper { self: I18nHelper with SecurityHelper =>

  def isProd: Boolean
  def isStage: Boolean

  def minifiedAssets = isProd || isStage

  def netDomain: lila.common.config.NetDomain
  lazy val assetDomain    = env.net.assetDomain
  lazy val socketDomains  = env.net.socketDomains
  lazy val vapidPublicKey = env.push.vapidPublicKey

  lazy val sameAssetDomain = netDomain.value == assetDomain.value

  lazy val assetBaseUrl = env.net.assetBaseUrl

  def assetVersion = AssetVersion.current

  def baseAssetUrl(path: String): String = s"${env.net.baseUrl}/assets/_$assetVersion/$path"
  def assetUrl(path: String): String     = s"$assetBaseUrl/assets/_$assetVersion/$path"

  def cdnUrl(path: String)    = s"$assetBaseUrl$path"
  def staticUrl(path: String) = s"$assetBaseUrl/assets/$path"

  def dbImageUrl(path: String) = s"$assetBaseUrl/image/$path"

  private def cssAt(path: String): Frag =
    link(href := assetUrl(path), tpe := "text/css", rel := "stylesheet")

  def cssTag(name: String): Frag =
    cssAt(s"css/$name.${if (minifiedAssets) "min" else "dev"}.css")

  def vendorCssTag(vendor: String, path: String): Frag =
    cssAt(s"vendors/${vendor}/${path}")

  private def jsAt(filePath: String): Frag =
    script(
      deferAttr,
      src := assetUrl(filePath),
    )

  def jsTag(name: String): Frag =
    jsAt(s"js/lishogi.${name}${minifiedAssets ?? ".min"}.js")

  private def moduleName(name: String): String =
    name.split("[.-]").toList match {
      case head :: tail => head + tail.map(_.capitalize).mkString
      case Nil          => name
    }

  def moduleJsTag(name: String, data: JsValue)(implicit ctx: Context): Frag =
    moduleJsTag(name, data, ctx.nonce)

  def moduleJsTag(name: String, data: JsValue, nonceOpt: Option[Nonce]): Frag =
    frag(
      jsTag(name),
      embedJsUnsafe(
        s"""window.lishogi.modulesData['${moduleName(name)}']=${safeJsonValue(data)}""",
        nonceOpt,
      ),
    )

  lazy val chartTag       = jsTag("chart")
  lazy val captchaTag     = jsTag("misc.captcha")
  lazy val roundNvuiTag   = jsTag("round.nvui")
  lazy val analyseNvuiTag = jsTag("analyse.nvui")
  lazy val flatpickrTag   = jsTag("misc.flatpickr")

  def vendorJsTag(vendor: String, path: String): Frag =
    jsAt(s"vendors/${vendor}/${path}")

  lazy val jQueryTag         = vendorJsTag("jquery", "jquery.min.js")
  lazy val howlerTag         = vendorJsTag("howler", "howler.core.min.js")
  lazy val powertipTag       = vendorJsTag("jquery-powertip", "jquery.powertip.min.js")
  lazy val infiniteScrollTag = vendorJsTag("infinite-scroll", "infinite-scroll.pkgd.min.js")
  lazy val fingerprintTag    = vendorJsTag("fipr", "fipr.js")
  lazy val tagifyTag         = vendorJsTag("tagify", "tagify.js")

  def i18nJsTag(name: String)(implicit lang: Lang): Frag =
    jsAt(s"i18n/${name}/${lang.code}.js")

  private def pieceSprite(name: String, filePath: String): Frag =
    link(
      id   := name,
      href := assetUrl(filePath),
      tpe  := "text/css",
      rel  := "stylesheet",
    )

  def defaultPieceSprite(implicit ctx: Context): Frag = defaultPieceSprite(ctx.currentPieceSet)
  def defaultPieceSprite(ps: lila.pref.PieceSet): Frag =
    pieceSprite("piece-sprite", s"piece-css/standard/$ps.css")

  def chuPieceSprite(implicit ctx: Context): Frag = chuPieceSprite(ctx.currentChuPieceSet)
  def chuPieceSprite(ps: lila.pref.PieceSet): Frag =
    pieceSprite("chu-piece-sprite", s"piece-css/chushogi/$ps.css")

  def kyoPieceSprite(implicit ctx: Context): Frag = kyoPieceSprite(ctx.currentKyoPieceSet)
  def kyoPieceSprite(ps: lila.pref.PieceSet): Frag =
    pieceSprite("kyo-piece-sprite", s"piece-css/kyotoshogi/$ps.css")

  def spriteSvg(categ: String, key: String): Frag =
    tag("svg")(cls := "extra-icon")(
      tag("use")(href := baseAssetUrl(s"icons/${categ}.svg#$key")),
    )

  def flagImage(code: String) =
    img(cls := "flag", src := staticUrl(s"images/flags/$code.svg"))

  def basicCsp(implicit req: RequestHeader): ContentSecurityPolicy = {
    val assets = if (req.secure) s"https://$assetDomain" else assetDomain.value
    val sockets = socketDomains map { socketDomain =>
      val protocol = if (req.secure) "wss://" else "ws://"
      s"$protocol$socketDomain"
    }
    ContentSecurityPolicy(
      defaultSrc = List("'self'", assets),
      connectSrc = "'self'" :: assets :: sockets ::: env.insightsEndpoint :: Nil,
      styleSrc = List("'self'", "'unsafe-inline'", assets),
      fontSrc = List("'self'", assetDomain.value, "https://fonts.gstatic.com"),
      frameSrc = List("'self'", assets, "https://www.youtube.com"),
      workerSrc = List("'self'", assets, "blob:"),
      imgSrc = List("data:", "*"),
      scriptSrc = List("'self'", assets),
      baseUri = List("'none'"),
    )
  }

  def defaultCsp(implicit ctx: Context): ContentSecurityPolicy = {
    val csp = basicCsp(ctx.req)
    ctx.nonce.fold(csp)(csp.withNonce(_))
  }

  def embedJsUnsafe(js: String)(implicit ctx: Context): Frag =
    embedJsUnsafe(js, ctx.nonce)

  def embedJsUnsafe(js: String, nonceOpt: Option[Nonce]): Frag =
    raw {
      val nonce = nonceOpt ?? { nonce =>
        s""" nonce="$nonce""""
      }
      s"""<script$nonce>$js</script>"""
    }

  val spinner: Frag = raw(
    """<div class="spinner"><svg viewBox="-2.5 -2.5 45 55" xmlns="http://www.w3.org/2000/svg">
  <path d="M 20 0 L 33 4 L 40 50 L 0 50 L 7 4 Z"
    style="fill:none;stroke-width:2.5;stroke-opacity:1;" />
</svg></div>""",
  )
}
