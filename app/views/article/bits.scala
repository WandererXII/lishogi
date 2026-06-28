package views.html
package article

import controllers.routes

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.article.Article
import lila.i18n.LangList

object bits {

  def langSelect(langCode: String)(implicit ctx: Context) = {
    views.html.base.bits.mselect(
      "langs",
      if (Article.Lang.allCode == langCode) trans.allLanguages.txt()
      else span(LangList.nameByStr(langCode)),
      ((
        Article.Lang.allCode,
        trans.allLanguages.txt(),
      ) :: LangList.allByLangCodesSorted.toList) map { case (code, name) =>
        a(href := routes.Article.recent(code), cls := (langCode == code).option("current"))(
          name,
        )
      },
    )
  }

  private[article] def menu(active: String)(implicit
      ctx: Context,
  ) = {
    st.aside(cls := "page-menu__menu subnav")(
      a(cls := active.active("recent"), href := routes.Article.default())(
        trans.article.recentArticles(),
      ),
      a(cls := active.active("best"), href := routes.Article.bestDefault())(
        trans.article.bestArticles(),
      ),
      ctx.me map { me =>
        a(cls := active.active("mine"), href := routes.Article.author(me.id))(
          trans.article.myArticles(),
        )
      },
      ctx.isAuth option a(cls := active.active("mineLikes"), href := routes.Article.mineLikes())(
        trans.article.likedArticles(),
      ),
      isGranted(_.ArticleMod) option a(
        cls  := active.active("submitted"),
        href := routes.Article.submitted(),
      )(
        trans.article.submittedArticles(),
      ),
      isGranted(_.ArticleMod) option a(
        cls  := active.active("system"),
        href := routes.Article.system(),
      )(
        "System",
      ),
    )
  }

  private val widgetImageSizeMultiplier = 35
  def widget(
      preview: Article.Preview,
      langCode: Option[Article.Lang.Code],
  )(implicit ctx: Context) = {
    val l = Article.Lang.resolve(preview.langCodes, langCode getOrElse ctx.lang.language)
    a(
      cls := List(
        "official" -> preview.categories.contains(Article.Category.Official),
      ),
      href := routes.Article.show(preview.id, l, preview.slug(l)),
    )(
      !preview.isSystemPage option preview
        .image(l)
        .fold[Frag](raw(lila.article.Gradient.div(preview.id)))(i =>
          st.img(
            attr("loading") := "lazy",
            src := (urlOrImageStorageUrl(
              i,
              lila.common.ImageStorage.Imgproxy
                .opts(
                  width = 16 * widgetImageSizeMultiplier,
                  height = 9 * widgetImageSizeMultiplier,
                  quality = 80,
                )
                .some,
            )),
          ),
        ),
      div(cls := "content")(
        div(cls := "title")(preview.title(l)),
        div(cls := "desc")(
          !preview.isSystemPage option s"${usernameOrId(preview.author)} - ",
          preview.langCodes.toList.map(l => lila.i18n.LangList.nameByStr(l)).mkString(" - "),
          !preview.isSystemPage option semanticDate(
            preview.publishedAt.getOrElse(preview.updatedAt),
          ),
        ),
      ),
      (preview.state != lila.article.Article.State.Published) option div(cls := "ribbon")(
        preview.state.key,
      ),
    )
  }

  def internalArticleButtons(
      article: Article,
      langCode: Article.Lang.Code,
  )(implicit ctx: Context) =
    isGranted(_.ArticleMod) option frag(
      a(
        cls   := "button",
        href  := routes.Article.translate(article.id, langCode),
        title := trans.article.translateArticle.txt(),
      )(trans.article.translateArticle()),
      a(
        cls   := "button",
        href  := routes.Article.edit(article.id, langCode),
        title := trans.article.editArticle.txt(),
      )(trans.article.editArticle()),
    )

}
