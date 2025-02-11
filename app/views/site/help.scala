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
          (lila.pref.PieceSet.all ::: lila.pref.ChuPieceSet.all ::: lila.pref.KyoPieceSet.all)
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
            h1(id := "embed-tv")("Embed Lishogi TV in your site"),
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
                dataIcon := "\"",
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
            h1(id := "embed-puzzle")("Embed the daily puzzle in your site"),
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
                dataIcon := "\"",
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
            h1(id := "embed-study")("Embed a shogi analysis in your site"),
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
            h1("Embed a shogi game in your site"),
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
          h1("HTTP API"),
          p(
            raw(
              """WIP - Lishogi exposes a RESTish HTTP/JSON API that you are welcome to use. Read the <a href="/api" class="blue">HTTP API documentation (WIP)</a>.""",
            ),
          ),
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
      val external             = frag(" ", i(dataIcon := "0"))
      def activeCls(c: String) = cls := active.activeO(c)
      main(cls := "page-menu")(
        st.nav(cls := "page-menu__menu subnav")(
          a(activeCls("about"), href := routes.Prismic.about)(trans.aboutX("lishogi.org")),
          a(activeCls("faq"), href := routes.Main.faq)(trans.faq.faqAbbreviation()),
          a(activeCls("contact"), href := routes.Main.contact)(trans.contact.contact()),
          a(activeCls("resources"), href := routes.Prismic.resources)(trans.shogiResources()),
          a(activeCls("tos"), href := routes.Prismic.tos)(trans.termsOfService()),
          a(activeCls("privacy"), href := routes.Prismic.privacy)(trans.privacy()),
          // a(activeCls("master"), href := routes.Prismic.master)("Title verification"),
          sep,
          a(activeCls("source"), href := routes.Prismic.source)(trans.sourceCode()),
          a(activeCls("help"), href := routes.Prismic.help)(trans.contribute()),
          a(activeCls("thanks"), href := routes.Prismic.thanks)(trans.thankYou()),
          sep,
          a(activeCls("webmasters"), href := routes.Main.webmasters)(trans.webmasters()),
          // a(activeCls("database"), href := "https://database.lishogi.org")(trans.database(), external),
          a(activeCls("api"), href := routes.Api.index)("API", external),
          sep,
          a(activeCls("lag"), href := routes.Main.lag)(trans.lag.isLishogiLagging()),
        ),
        div(cls := s"page-menu__content $contentCls")(body),
      )
    }
}
