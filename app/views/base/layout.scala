package views.html.base

import controllers.routes

import org.joda.time.DateTime
import org.joda.time.DateTimeZone

import lila.api.AnnounceStore
import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.common.CanonicalPath
import lila.common.ContentSecurityPolicy
import lila.common.Holiday
import lila.common.String.html.safeJsonValue

object layout {

  object bits {
    val doctype    = raw("<!DOCTYPE html>")
    val topComment = raw("""<!-- Lishogi is open source! See https://lishogi.org/source -->""")
    val charset    = raw("""<meta charset="utf-8">""")
    val viewport = raw(
      """<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">""",
    )
    def metaCsp(csp: ContentSecurityPolicy): Frag =
      raw {
        s"""<meta http-equiv="Content-Security-Policy" content="$csp">"""
      }
    def metaCsp(csp: Option[ContentSecurityPolicy])(implicit ctx: Context): Frag =
      metaCsp(csp getOrElse defaultCsp)
    def metaThemeColor(implicit ctx: Context): Frag =
      raw {
        if (ctx.pref.background == "system") {
          s"""<meta name="theme-color" content="#dbd7d1" media="(prefers-color-scheme: light)">
      <meta name="theme-color" content="#2e2a24" media="(prefers-color-scheme: dark)">"""
        } else {
          s"""<meta name="theme-color" content="${if (ctx.pref.isLightBackground) "#dbd7d1"
            else "#2e2a24"}">"""
        }
      }
    def backgroundClass(bg: String, activeCustomBackground: Option[lila.pref.CustomBackground]) =
      activeCustomBackground
        .fold(bg)(cb => s"${bg}-${if (cb.light) "light" else "dark"}")
    def cssVariables(
        zoom: Option[Int],
        customTheme: Option[lila.pref.CustomTheme],
        customBackground: Option[lila.pref.CustomBackground],
        bgImgUrl: Option[String],
    ): Option[String] = {
      val variables = List(
        zoom.map(z => s"--zoom:${z};"),
        bgImgUrl.map(url => s"--custom-bg-img:${url};"),
        customTheme.map(_.toVars),
        customBackground.map(_.toVars),
      ).flatten

      variables.nonEmpty option variables.mkString
    }

    val windowLishogi: String =
      """window.lishogi={ready:new Promise(r=>document.addEventListener("DOMContentLoaded",r)),modulesData:{}}"""
  }
  import bits._

  private val noTranslate = raw("""<meta name="google" content="notranslate">""")

  private def preload(href: String, as: String, crossorigin: Boolean, tpe: Option[String] = None) =
    raw(s"""<link rel="preload" href="$href" as="$as" ${tpe.??(t =>
        s"""type="$t" """,
      )}${crossorigin ?? "crossorigin"}>""")

  private val iconsPreload = frag(
    preload(assetUrl("icons/lishogi.woff2"), "font", crossorigin = true, "font/woff2".some),
  )

  private def boardPreload(implicit ctx: Context) =
    ctx.currentTheme.file map { file =>
      preload(assetUrl(s"images/boards/$file"), "image", crossorigin = false)
    }

  private val manifests = raw(
    """<link rel="manifest" href="/manifest.json"><meta name="twitter:site" content="@lishogi">""",
  )

  private val jsLicense = raw("""<link rel="jslicense" href="/source">""")

  private val favicons = raw {
    List(512, 256, 192, 128, 64)
      .map { px =>
        s"""<link rel="icon" type="image/png" href="${staticUrl(
            s"logo/lishogi-favicon-$px.png",
          )}" sizes="${px}x${px}">"""
      }
      .mkString(
        "",
        "",
        s"""<link id="favicon" rel="icon" type="image/png" href="${staticUrl(
            "logo/lishogi-favicon-32.png",
          )}" sizes="32x32">""",
      )
  }
  private def blindModeForm(implicit ctx: Context) =
    raw(s"""<form id="blind-mode" action="${routes.Main.toggleBlindMode}" method="POST"><input type="hidden" name="enable" value="${if (
        ctx.blind
      )
        0
      else
        1}"><input type="hidden" name="redirect" value="${ctx.req.path}"><button type="submit">Accessibility: ${if (
        ctx.blind
      )
        "Disable"
      else "Enable"} blind mode</button></form>""")
  private val zenToggle =
    a(
      id       := "zentog",
      cls      := "text fbt active",
      dataIcon := Icons.correct,
      "ZEN MODE",
    )

  private def dasher(me: lila.user.User) =
    div(
      cls := "dasher",
      a(
        id  := "user_tag",
        cls := "toggle link",
      )(me.username),
      div(
        id  := "dasher_app",
        cls := "dropdown",
      ),
    )

  private def allNotifications(implicit ctx: Context) =
    frag(
      div(
        a(
          id  := "challenge-toggle",
          cls := "toggle link",
        )(
          span(
            title     := trans.challenges.txt(),
            cls       := "data-count",
            dataCount := ctx.nbChallenges,
            dataIcon  := Icons.challenge,
          ),
        ),
        div(
          id  := "challenge-app",
          cls := "dropdown",
        ),
      ),
      div(
        a(
          id  := "notify-toggle",
          cls := "toggle link",
        )(
          span(
            title     := trans.notifications.txt(),
            cls       := "data-count",
            dataCount := ctx.nbNotifications,
            dataIcon  := Icons.bell,
          ),
        ),
        div(
          id  := "notify-app",
          cls := "dropdown",
        ),
      ),
    )

  private def anonDasher(implicit ctx: Context) =
    frag(
      a(cls := "link", href := routes.User.newPlayer)(span(dataIcon := Icons.infoCircle)),
      div(cls := "signin-signup")(
        a(
          href := langHref(
            routes.Auth.signup.url,
          ),
          cls := "signup button",
        )(trans.signUp.txt()),
        a(
          href := langHref(
            s"${routes.Auth.login.url}?referrer=${ctx.req.path}",
          ),
          cls := "signin button",
        )(trans.signIn.txt()),
      ),
      div(cls := "dasher")(
        a(cls := "toggle link anon")(
          span(
            title    := trans.preferences.preferences.txt(),
            dataIcon := Icons.gear,
          ),
        ),
        div(
          id  := "dasher_app",
          cls := "dropdown",
        ),
      ),
    )

  private val clinputLink = a(cls := "link")(span(dataIcon := Icons.search))

  private def clinput(implicit ctx: Context) =
    div(id := "clinput")(
      clinputLink,
      input(
        id           := "lishogi-clinput",
        spellcheck   := "false",
        autocomplete := ctx.blind.toString,
        aria.label   := trans.search.search.txt(),
        placeholder  := trans.search.search.txt(),
      ),
    )

  private def switchLanguage(implicit ctx: Context) =
    spaceless(s"""<form method="post" action="/translation/select" class="header-langs">
    ${if (ctx.lang.language == "en")
        """<button type="submit" name="lang" value="ja-JP" title="ja-JP">日本語</button>"""
      else """<button type="submit" name="lang" value="en-US" title="en-US">English</button>"""}
    </form>""")

  private def canonical(canonicalPath: CanonicalPath)(implicit ctx: Context) = raw {
    val langQuery = ctx.req
      .getQueryString("lang")
      .flatMap(lila.i18n.I18nLangPicker.byQuery)
      .filterNot(_.language == "en")
      .fold("") { l =>
        s"?lang=${lila.i18n.languageCode(l)}"
      }
    s"""<link rel="canonical" href="$netBaseUrl${canonicalPath.value}$langQuery" />"""
  }

  private def hrefLang(langCode: String, pathWithQuery: String) =
    s"""<link rel="alternate" hreflang="$langCode" href="$netBaseUrl$pathWithQuery"/>"""

  private def defaultWithEnHrefLang(path: String) =
    hrefLang("x-default", path) + hrefLang("en", path)

  private def hrefLangs(altLangs: lila.i18n.LangList.AlternativeLangs)(implicit ctx: Context) =
    raw {
      val path = ctx.req.path
      altLangs match {
        case lila.i18n.LangList.EnglishJapanese =>
          defaultWithEnHrefLang(path) + hrefLang("ja", s"$path?lang=ja")
        case lila.i18n.LangList.All =>
          defaultWithEnHrefLang(path) + (lila.i18n.LangList.alternativeHrefLangCodes.map {
            langCode =>
              hrefLang(langCode, s"$path?lang=$langCode")
          }).mkString
        case lila.i18n.LangList.Custom(langPathMap) =>
          (langPathMap.map { case (langCode, path) =>
            if (langCode == "en") defaultWithEnHrefLang(path)
            else hrefLang(langCode, path)
          }).mkString
      }
    }

  private val vendorSiteJs = frag(
    jQueryTag,
    howlerTag,
    powertipTag,
  )

  private val spaceRegex              = """\s{2,}+""".r
  private def spaceless(html: String) = raw(spaceRegex.replaceAllIn(html.replace("\\n", ""), ""))

  private val dataVapid         = attr("data-vapid")
  private val dataUser          = attr("data-user")
  private val dataSocketDomains = attr("data-socket-domains")
  private val dataNonce         = attr("data-nonce")
  private val dataAnnounce      = attr("data-announce")
  val dataColorName             = attr("data-color-name")
  val dataNotation              = attr("data-notation")
  val dataSoundSet              = attr("data-sound-set")
  val dataClockSoundSet         = attr("data-clock-sound-set")
  val dataTheme                 = attr("data-theme")
  val dataBoardTheme            = attr("data-board-theme")
  val dataPieceSet              = attr("data-piece-set")
  val dataChuPieceSet           = attr("data-chu-piece-set")
  val dataKyoPieceSet           = attr("data-kyo-piece-set")
  val dataDobutsuPieceSet       = attr("data-dobutsu-piece-set")
  val dataAssetUrl              = attr("data-asset-url")
  val dataAssetVersion          = attr("data-asset-version")
  val dataDev                   = attr("data-dev")

  def apply(
      title: String,
      fullTitle: Option[String] = None,
      robots: Boolean = isGloballyCrawlable,
      moreCss: Frag = emptyFrag,
      moreJs: Frag = emptyFrag,
      playing: Boolean = false,
      openGraph: Option[lila.app.ui.OpenGraph] = None,
      shogiground: Boolean = true,
      zoomable: Boolean = false,
      csp: Option[ContentSecurityPolicy] = None,
      wrapClass: String = "",
      canonicalPath: Option[CanonicalPath] = None,
      withHrefLangs: Option[lila.i18n.LangList.AlternativeLangs] = None,
  )(body: Frag)(implicit ctx: Context): Frag =
    frag(
      doctype,
      html(
        st.lang := lila.i18n.languageCode(ctx.lang),
        cls     := backgroundClass(ctx.currentBg.key, ctx.pref.activeCustomBackground),
        style := cssVariables(
          zoomable option ctx.zoom,
          ctx.pref.activeCustomTheme,
          ctx.pref.activeCustomBackground,
          ctx.pref.activeBgImgUrl,
        ),
      )(
        topComment,
        head(
          charset,
          viewport,
          metaCsp(csp),
          metaThemeColor,
          st.headTitle {
            if (ctx.blind) "lishogi"
            else if (isProd) fullTitle | s"$title | lishogi.org"
            else s"[dev] ${fullTitle | s"$title | lishogi.dev"}"
          },
          if (ctx.pref.isNewBackground) cssTag("common.variables-new")
          else cssTag("common.variables"),
          ctx.pref.activeCustomBackground.isDefined option cssTag("common.custom"),
          cssTag("misc.site"),
          ctx.pageData.inquiry.isDefined option cssTag("user.mod.inquiry"),
          ctx.userContext.impersonatedBy.isDefined option cssTag("user.mod.impersonate"),
          ctx.blind option cssTag("misc.blind"),
          defaultPieceSprite,
          moreCss,
          meta(
            content := openGraph.fold(trans.siteDescription.txt())(o => o.description),
            name    := "description",
          ),
          link(rel := "mask-icon", href := staticUrl("logo/lishogi.svg"), attr("color") := "black"),
          favicons,
          !robots option raw("""<meta content="noindex, nofollow" name="robots">"""),
          noTranslate,
          openGraph.map(_.frags(ctx.lang)),
          iconsPreload,
          boardPreload,
          manifests,
          jsLicense,
          canonicalPath.ifTrue(robots).map(canonical),
          withHrefLangs
            .ifTrue {
              robots &&
              ctx.req.queryString.removed("lang").isEmpty &&
              canonicalPath.fold(true)(_.value == ctx.req.path)
            }
            .map(hrefLangs),
          embedJsUnsafe(windowLishogi),
          ctx.requiresFingerprint option fingerprintTag,
          vendorSiteJs,
          shogiground option vendorJsTag("shogiground", "shogiground.min.js"),
          i18nJsTag("core"),
          jsTag("site"),
          moreJs,
          ctx.pageData.inquiry.isDefined option jsTag("user.inquiry"),
        ),
        st.body(
          cls := List(
            s"${ctx.currentTheme.cssClass} coords-${ctx.pref.coordsClass}" -> true,
            s"grid-width-${ctx.pref.gridWidth}" -> ctx.pref.activeCustomTheme.isDefined,
            "custom-background-img"             -> ctx.pref.activeBgImgUrl.isDefined,
            "thick-grid"                        -> ctx.pref.isUsingThickGrid,
            "clear-hands"                       -> ctx.pref.clearHands,
            "hands-background"                  -> ctx.pref.handsBackground,
            "no-touch"                          -> !ctx.pref.squareOverlay,
            "no-flags"                          -> ctx.pref.wantsNoFlags,
            "zen"                               -> ctx.pref.isZen,
            "blind-mode"                        -> ctx.blind,
            "kid"                               -> ctx.kid,
            "mobile"                            -> ctx.isMobileBrowser,
            "playing"                           -> playing,
          ),
          dataDev             := (!isProd).option("true"),
          dataVapid           := vapidPublicKey,
          dataUser            := ctx.userId,
          dataSoundSet        := ctx.currentSoundSet.toString,
          dataClockSoundSet   := ctx.currentClockSoundSet.toString,
          dataSocketDomains   := socketDomains.mkString(","),
          dataAssetUrl        := assetBaseUrl,
          dataAssetVersion    := assetVersion.value,
          dataNonce           := ctx.nonce.ifTrue(sameAssetDomain).map(_.value),
          dataTheme           := ctx.currentBg.key,
          dataBoardTheme      := ctx.currentTheme.key,
          dataPieceSet        := ctx.currentPieceSet.key,
          dataChuPieceSet     := ctx.currentChuPieceSet.key,
          dataKyoPieceSet     := ctx.currentKyoPieceSet.key,
          dataDobutsuPieceSet := ctx.currentDobutsuPieceSet.key,
          dataAnnounce        := AnnounceStore.get.map(a => safeJsonValue(a.json)),
          dataNotation        := ctx.pref.notation.toString,
          dataColorName       := ctx.pref.colorName.toString,
        )(
          blindModeForm,
          ctx.pageData.inquiry map { views.html.mod.inquiry(_) },
          ctx.me ifTrue ctx.userContext.impersonatedBy.isDefined map {
            views.html.mod.impersonate(_)
          },
          isStage option views.html.base.bits.stage,
          lila.security.EmailConfirm.cookie
            .get(ctx.req)
            .map(views.html.auth.bits.checkYourEmailBanner(_)),
          playing option zenToggle,
          siteHeader(),
          div(
            id := "main-wrap",
            cls := List(
              wrapClass -> wrapClass.nonEmpty,
            ),
          )(body),
          ctx.isAuth option div(
            id := "friend_box",
          )(
            div(cls := "friend_box_title")(
              iconTag(Icons.up),
              trans.friends(),
            ),
            div(cls := "content_wrap none")(
              div(cls := "content list"),
            ),
          ),
          a(id := "reconnecting", cls := "link text", dataIcon := Icons.loop),
        ),
      ),
    )

  object siteHeader {

    private val topnavToggle = spaceless(
      """
<input type="checkbox" id="tn-tg" class="topnav-toggle fullscreen-toggle" aria-label="Navigation">
<label for="tn-tg" class="fullscreen-mask"></label>
<label for="tn-tg" class="hbg"><span class="hbg__in"></span></label>""",
    )

    private def reports(implicit ctx: Context) =
      isGranted(_.SeeReport) option
        a(
          cls       := "link data-count link-center",
          title     := "Moderation",
          href      := routes.Report.list,
          dataCount := blockingReportNbOpen,
          dataIcon  := Icons.agent,
        )

    private def teamRequests(implicit ctx: Context) =
      ctx.teamNbRequests > 0 option
        a(
          cls       := "link data-count link-center",
          href      := routes.Team.requests,
          dataCount := ctx.teamNbRequests,
          dataIcon  := Icons.people,
          title     := trans.team.teams.txt(),
        )

    // For Japanese let's force lang param - to build backlinks for SEO - for now
    // unless JP comes from param itself, langHref will take care of that
    private def siteUrl(implicit ctx: Context) =
      if (ctx.isJapanese && ctx.req.getQueryString("lang").isEmpty)
        urlWithLangQuery("/", "ja")
      else langHref("/")

    def apply()(implicit ctx: Context) = {
      val isRoot = ctx.req.path == "/"
      // date good enough
      val holiday = isRoot ?? Holiday.find(
        if (ctx.isJapanese) DateTime.now(DateTimeZone.forID("Asia/Tokyo")) else DateTime.now,
      )

      header(id := "top")(
        div(cls := "site-title-nav")(
          topnavToggle,
          (if (isRoot) h1 else div) (cls := "site-title", title := "lishogi.org")(
            a(href := siteUrl)(
              i(
                title := holiday.map(h => h.trans),
                cls := List(
                  "site-icon" -> true,
                  "bot"       -> ctx.isBot,
                  "kiddo"     -> ctx.kid,
                ) ++ holiday.map(h => h.key -> true),
                dataIcon := Icons.logo,
              ),
              "lishogi",
              span(if (isProd) ".org" else ".dev"),
            ),
          ),
          ctx.blind option h2("Navigation"),
          topnav(),
        ),
        div(cls := "site-buttons")(
          (ctx.isAnon && ctx.req.path == "/") option switchLanguage,
          clinput,
          reports,
          teamRequests,
          ctx.me map { me =>
            frag(allNotifications, dasher(me))
          } getOrElse { !ctx.pageData.error option anonDasher },
        ),
      )
    }
  }

}
