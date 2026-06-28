package views.html.site

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.article.Article

object page {

  def apply(
      key: String,
      article: Article,
      renderedBody: String,
      langCode: Article.Lang.Code,
  )(implicit
      ctx: Context,
  ) =
    views.html.base.layout(
      moreCss = cssTag("misc.page"),
      title = article.title(langCode),
      withHrefLangs = lila.i18n.LangList.Only(article.langCodes.toList).some,
    ) {
      main(cls := s"page box box-pad page-${key}")(
        h1(article.title(langCode)),
        div(cls := "intro")(article.intro(langCode)),
        div(cls := "body")(raw(renderedBody)),
      )
    }
}
