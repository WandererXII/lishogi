package views.html.site

import controllers.routes

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._

object help {

  def page(active: String, doc: lila.prismic.Document, resolver: lila.prismic.DocumentLinkResolver)(
      implicit ctx: Context,
  ) = {
    val title = ~doc.getText("doc.title")
    layout(
      title = title,
      active = active,
      contentCls = "page box box-pad",
      moreCss = cssTag("misc.page"),
    )(
      frag(
        h1(title),
        div(cls := "body")(raw(~doc.getHtml("doc.content", resolver))),
      ),
    )
  }

  def source(doc: lila.prismic.Document, resolver: lila.prismic.DocumentLinkResolver)(implicit
      ctx: Context,
  ) = {
    val title = ~doc.getText("doc.title")
    layout(
      title = title,
      active = "source",
      moreCss = cssTag("misc.source"),
      contentCls = "page",
    )(
      frag(
        st.section(cls := "box box-pad body")(
          h1(title),
          raw(~doc.getHtml("doc.content", resolver)),
        ),
        br,
        st.section(cls := "box")(
          div(cls := "box__top")(
            h2("lila version"),
          ),
          table(cls := "slist slist-pad")(
            tr(
              td("Boot"),
              td(momentFromNow(lila.common.Uptime.startedAt)),
            ),
          ),
        ),
      ),
    )
  }

  def webmasters()(implicit ctx: Context) = {
    val parameters = frag(
      p("Parameters:"),
      ul(
        li(strong("theme"), ": ", lila.pref.Theme.all.map(_.key).mkString(", ")),
        li(
          strong("pieceSet"),
          ": ",
          lila.pref.PieceSet.all
            .map(_.key)
            .mkString(", "),
        ),
        li(strong("bg"), ": light, dark"),
      ),
    )
    layout(
      title = "Webmasters",
      active = "webmasters",
      moreCss = cssTag("misc.page"),
      contentCls = "page",
    )(
      frag(
        div(cls := "box box-pad developers body") {
          val args =
            """style="width: 400px; height: calc(400px / 9 * 11 / 11 * 12 + 2vmin);" allowtransparency="true" frameborder="0""""
          frag(
            h2(id := "embed-tv")("Embed Lishogi TV in your site"),
            div(cls := "center")(raw(s"""<iframe src="/tv/frame?theme=wood" $args></iframe>""")),
            p("Add the following HTML to your site:"),
            p(cls := "copy-zone")(
              input(
                id  := "tv-embed-src",
                cls := "copyable autoselect",
                value := s"""<iframe src="$netBaseUrl/tv/frame?theme=wood&bg=light&pieceSet=Ryoko_1Kanji" $args></iframe>""",
              ),
              button(
                title    := "Copy code",
                cls      := "copy button",
                dataRel  := "tv-embed-src",
                dataIcon := Icons.copy,
              ),
            ),
            parameters,
          )
        },
        br,
        div(cls := "box box-pad developers body") {
          val args =
            """style="width: 400px; height: calc(400px / 9 * 11 / 11 * 12 + 2vmin);" allowtransparency="true" frameborder="0""""
          frag(
            h2(id := "embed-puzzle")("Embed the daily puzzle in your site"),
            div(cls := "center")(
              raw(s"""<iframe src="/training/frame?theme=wood" $args></iframe>"""),
            ),
            p("Add the following HTML to your site:"),
            p(cls := "copy-zone")(
              input(
                id  := "puzzle-embed-src",
                cls := "copyable autoselect",
                value := s"""<iframe src="$netBaseUrl/training/frame?theme=wood&pieceSet=Ryoko_1Kanji" $args></iframe>""",
              ),
              button(
                title    := "Copy code",
                cls      := "copy button",
                dataRel  := "puzzle-embed-src",
                dataIcon := Icons.copy,
              ),
            ),
            parameters,
            p("The text is automatically translated to your visitor's language."),
          )
        },
        br,
        div(cls := "box box-pad developers body") {
          val args = """style="width: 600px; height: 397px;" frameborder="0""""
          frag(
            h2(id := "embed-study")("Embed a shogi analysis in your site"),
            raw(
              s"""<iframe src="/study/embed/O591ZfdK/ciASxN2A?bg=auto&theme=auto" $args></iframe>""",
            ),
            p(
              "Create ",
              a(href := routes.Study.allDefault(1))("a study"),
              ", then click the share button to get the HTML code for the current chapter.",
            ),
            parameters,
            p("The text is automatically translated to your visitor's language."),
          )
        },
        br,
        div(cls := "box box-pad developers body") {
          val args = """style="width: 600px; height: 397px;" frameborder="0""""
          frag(
            h2("Embed a shogi game in your site"),
            raw(s"""<iframe src="/embed/sFbJtorq?bg=auto&theme=auto" $args></iframe>"""),
            p(
              raw(
                """On a game analysis page, click the <em>"Export"</em> tab at the bottom, then """,
              ),
              "\"",
              em(trans.embedInYourWebsite(), "\"."),
            ),
            parameters,
            p("The text is automatically translated to your visitor's language."),
          )
        },
        br,
        div(cls := "box box-pad developers body")(
          a(href := assetUrl("logo/lishogi.zip"), cls := "text", dataIcon := Icons.download)(
            trans.contact.displayLishogiLogo(),
          ),
        ),
        br,
        div(cls := "box box-pad developers body")(
          h2("HTTP API"),
          p(
            raw(
              """WIP - Lishogi exposes a RESTish HTTP/JSON API that you are welcome to use. Read the <a href="/api" class="blue">HTTP API documentation (WIP)</a>.""",
            ),
          ),
        ),
      ),
    )
  }

  def friendlySites(
      doc: lila.prismic.Document,
      resolver: lila.prismic.DocumentLinkResolver,
  )(implicit ctx: Context) =
    layout(
      title = trans.contact.friendlySites.txt(),
      active = "friendly-sites",
      moreCss = frag(
        cssTag("misc.page"),
        cssTag("misc.friendly-sites"),
      ),
      contentCls = "page box box-pad",
    ) {
      frag(
        h1(trans.contact.friendlySites()),
        div(cls := "body")(
          p(trans.contact.manyGreatShogiSites()),
          div(cls := "friendly-sites")(
            raw(~doc.getHtml("doc.content", resolver)),
          ),
          p(
            trans.contact.yourSiteHere(),
            br,
            trans.contact.sendEmailAt(contactEmail),
          ),
          a(href := assetUrl("logo/lishogi.zip"), cls := "text", dataIcon := Icons.download)(
            trans.contact.displayLishogiLogo(),
          ),
        ),
      )
    }

  def layout(
      title: String,
      active: String,
      contentCls: String = "",
      moreCss: Frag = emptyFrag,
      moreJs: Frag = emptyFrag,
  )(body: Frag)(implicit ctx: Context) =
    views.html.base.layout(
      title = title,
      moreCss = moreCss,
      moreJs = moreJs,
    ) {
      val sep                  = div(cls := "sep")
      def activeCls(c: String) = cls := active.activeO(c)
      main(cls := "page-menu")(
        st.nav(cls := "page-menu__menu subnav")(
          a(activeCls("about"), href := routes.Prismic.about)(trans.aboutX("lishogi.org")),
          a(activeCls("faq"), href := routes.Main.faq)(trans.faq.faqAbbreviation()),
          a(activeCls("contact"), href := routes.Main.contact)(trans.contact.contact()),
          a(activeCls("resources"), href := routes.Prismic.resources)(trans.shogiResources()),
          a(activeCls("tos"), href := routes.Prismic.tos)(trans.termsOfService()),
          a(activeCls("privacy"), href := routes.Prismic.privacy)(trans.privacy()),
          sep,
          a(activeCls("friendly-sites"), href := routes.Prismic.friendlySites)(
            trans.contact.friendlySites(),
          ),
          a(activeCls("source"), href := routes.Prismic.source)(trans.sourceCode()),
          a(activeCls("help"), href := routes.Prismic.help)(trans.contribute()),
          a(activeCls("thanks"), href := routes.Prismic.thanks)(trans.thankYou()),
          sep,
          a(activeCls("webmasters"), href := routes.Main.webmasters)(trans.webmasters()),
          // a(activeCls("database"), href := "https://database.lishogi.org")(trans.database(), external),
          a(activeCls("api"), href := routes.Api.index, target := "_blank")("API"),
          sep,
          a(activeCls("lag"), href := routes.Main.lag)(trans.lag.isLishogiLagging()),
        ),
        div(cls := s"page-menu__content $contentCls")(body),
      )
    }
}
