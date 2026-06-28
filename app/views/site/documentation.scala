package views.html.site

import controllers.routes

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.article.Article

object documentation {

  def article(
      key: String,
      article: Article,
      renderedBody: String,
      langCode: Article.Lang.Code,
  )(implicit
      ctx: Context,
  ) = {
    layout(
      title = article.title(langCode),
      active = key,
      contentCls = "page box box-pad",
      moreCss = frag(cssTag("misc.page")),
      moreJs = jsTag("misc.expand-text"),
      openGraph = lila.app.ui
        .OpenGraph(
          title = article.title(langCode),
          url = s"$netBaseUrl${routes.Article.documentation(key)}",
          description = article.intro(langCode),
        )
        .some,
      withHrefLangs = lila.i18n.LangList.Only(article.langCodes.toList).some,
    )(
      frag(
        views.html.article.bits.internalArticleButtons(article, langCode),
        h1(article.title(langCode)),
        div(cls := "intro")(article.intro(langCode)),
        div(cls := "body")(raw(renderedBody)),
      ),
    )
  }

  def ranks(implicit ctx: Context) = layout(
    title = trans.ranks.txt(),
    active = "ranks",
    contentCls = "page box box-pad",
    moreCss = frag(cssTag("misc.page")),
    moreJs = jsTag("misc.expand-text"),
  )(
    frag(
      h1(trans.ranks()),
      div(cls := "body")(
        p(trans.ranksAfterStable()),
        br,
        table(cls := "normal-table ranks-table")(
          thead(
            tr(
              th("日本語"),
              th("English"),
              th(trans.rating()),
            ),
          ),
          tbody(
            for (rank <- lila.rating.Rank.all.toList)
              yield tr(
                td(rankTag(rank)(lila.i18n.jaLang)),
                td(rankTag(rank)(lila.i18n.enLang)),
                td(
                  if (rank.max == lila.rating.Glicko.maxRating) s"${rank.min}+"
                  else s"${rank.min} - ${rank.max - 1}",
                ),
              ),
          ),
        ),
      ),
    ),
  )

  private def layout(
      title: String,
      active: String,
      contentCls: String,
      moreCss: Frag,
      moreJs: Frag,
      openGraph: Option[lila.app.ui.OpenGraph] = None,
      withHrefLangs: Option[lila.i18n.LangList.AlternativeLangs] = none,
  )(body: Frag)(implicit ctx: Context) =
    views.html.base.layout(
      title = title,
      moreCss = moreCss,
      moreJs = moreJs,
      openGraph = openGraph,
      withHrefLangs = withHrefLangs,
    ) {
      val sep                  = div(cls := "sep")
      def activeCls(c: String) = cls := active.activeO(c)
      main(cls := "page-menu")(
        st.nav(cls := "page-menu__menu subnav")(
          a(activeCls("rules"), href := routes.Article.documentation("rules"))(trans.rules()),
          sep,
          a(activeCls("impasse"), href := routes.Article.documentation("impasse"))(trans.impasse()),
          sep,
          a(activeCls("kif"), href := routes.Article.documentation("kif"))(trans.kif()),
          a(activeCls("csa"), href := routes.Article.documentation("csa"))(trans.csa()),
          sep,
          a(activeCls("ranks"), href := routes.Main.ranks)(trans.ranks()),
          sep,
          frag(
            shogi.variant.Variant.all.withFilter(!_.standard) map { v =>
              a(
                activeCls(v.key),
                href := routes.Article.documentation(v.key),
              )(variantName(v))
            },
          ),
        ),
        div(cls := s"page-menu__content $contentCls")(body),
      )
    }
}
