package views.html.article

import controllers.routes
import play.api.data.Form

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.article.Article
import lila.article.ArticleForm

object form {

  def create(form: Form[ArticleForm.ContentSetup])(implicit
      ctx: Context,
  ) =
    views.html.base.layout(
      title = trans.article.newArticle.txt(),
      moreCss = cssTag("misc.article.form"),
      moreJs = frag(jsTag("misc.article-form")),
      csp = defaultCsp
        .copy(
          connectSrc = env.imgDomain :: defaultCsp.connectSrc,
        )
        .some,
    ) {
      main(cls := "box box-pad page-small article-form")(
        h1(
          a(
            href     := routes.Article.default(),
            dataIcon := Icons.left,
            cls      := "text",
          ),
          trans.article.newArticle(),
        ),
        small(cls := "tos")(trans.article.shortTOS()),
        postForm(cls := "form3", action := routes.Article.create)(
          formContent(
            form,
            langChoices = lila.i18n.LangList.allByLangCodesSorted,
          ),
          form3.actions(
            a(href := routes.Article.default())(trans.cancel()),
            form3.submit(trans.article.saveAsDraft(), icon = Icons.article.some),
          ),
        ),
      )
    }

  def translate(
      form: Form[ArticleForm.ContentSetup],
      article: Article,
      langCode: Article.Lang.Code,
  )(implicit
      ctx: Context,
  ) = {
    val title = s"${trans.translate.txt()} - ${article.title(langCode)}"
    views.html.base.layout(
      title = title,
      moreCss = cssTag("misc.article.form"),
      moreJs = frag(jsTag("misc.article-form")),
      csp = defaultCsp
        .copy(
          connectSrc = env.imgDomain :: defaultCsp.connectSrc,
        )
        .some,
    ) {
      main(cls := "box box-pad page-small article-form")(
        h1(
          !article.isSystemPage option a(
            href     := routes.Article.show(article.id, langCode, article.slug(langCode)),
            dataIcon := Icons.left,
            cls      := "text",
          ),
          title,
        ),
        small(cls := "tos")(trans.article.shortTOS()),
        postForm(cls := "form3", action := routes.Article.translateApply(article.id, langCode))(
          formContent(
            form,
            langChoices = lila.i18n.LangList.allByLangCodesSorted.filterNot(l =>
              article.langCodes.contains(l._1),
            ),
            translateForm = true,
          ),
          form3.actions(
            a(href := routes.Article.show(article.id, langCode, article.slug(langCode)))(
              trans.cancel(),
            ),
            form3.submit(trans.save(), icon = Icons.article.some),
          ),
        ),
      )
    }
  }

  def edit(form: Form[ArticleForm.ContentSetup], article: Article, langCode: Article.Lang.Code)(
      implicit ctx: Context,
  ) = {
    val title = s"${trans.edit.txt()} - ${article.id}"
    views.html.base.layout(
      title = title,
      moreCss = cssTag("misc.article.form"),
      moreJs = frag(jsTag("misc.article-form")),
      csp = defaultCsp
        .copy(
          connectSrc = env.imgDomain :: defaultCsp.connectSrc,
        )
        .some,
    ) {
      main(cls := "box box-pad page-small article-form")(
        h1(
          !article.isSystemPage option a(
            href     := routes.Article.show(article.id, langCode, article.slug(langCode)),
            dataIcon := Icons.left,
            cls      := "text",
          ),
          title,
        ),
        small(cls := "tos")(trans.article.shortTOS()),
        postForm(cls := "form3", action := routes.Article.editApply(article.id, langCode))(
          formContent(
            form,
            langChoices = lila.i18n.LangList.allByLangCodesSorted.find(_._1 == langCode).toList,
            editForm = true,
          ),
          form3.actions(
            a(href := routes.Article.show(article.id, langCode, article.slug(langCode)))(
              trans.cancel(),
            ),
            form3.submit(trans.save(), icon = Icons.article.some),
          ),
        ),
        (article.langCodes.size > 1) option postForm(
          cls    := "delete delete-lang",
          action := routes.Article.deleteLang(article.id, langCode),
        )(
          submitButton(
            dataIcon := Icons.warning,
            cls      := s"text button button-red confirm",
          )(trans.article.deleteXLangOfArticle(langCode)),
        ),
        postForm(cls := "delete", action := routes.Article.deleteArticle(article.id))(
          submitButton(
            dataIcon := Icons.warning,
            cls      := s"text button button-red confirm",
          )(trans.article.deleteArticle()),
        ),
      )
    }
  }

  private def formContent(
      form: Form[ArticleForm.ContentSetup],
      langChoices: List[(Article.Lang.Code, String)],
      translateForm: Boolean = false,
      editForm: Boolean = false,
  )(implicit
      ctx: Context,
  ) = {
    val secret = lila.common.ImageStorage
      .uploadSecret(~ctx.userId, env.imgUploadKey);
    frag(
      globalError(form),
      form3.group(form("langCode"), trans.language())(
        form3.select(_, langChoices, disabled = editForm),
      ),
      translateForm option form3.group(form("translator"), trans.article.translator())(
        form3.input(_),
      ),
      form3.group(form("title"), trans.article.title())(form3.input(_)),
      form3.group(form("image"), trans.article.coverImage()) { f =>
        form3.imageUploader(f, secret)
      },
      form3.group(form("intro"), trans.article.introText(), help = trans.article.introHelp().some)(
        form3.textarea(_)(rows := 4),
      ),
      form3.group(form("body"), trans.article.bodyText()) { f =>
        frag(
          div(
            id                        := "markdown-editor",
            attr("data-mod")          := isGranted(_.ArticleMod).toString,
            attr("data-image-url")    := env.imgUploadUrl,
            attr("data-image-secret") := secret,
          ),
          form3.textarea(f)(),
        )
      },
    )
  }
}
