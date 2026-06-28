package views.html.article

import controllers.routes

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.article.Article

object show {

  def apply(
      article: Article,
      rendered: String,
      langCode: Article.Lang.Code,
      liked: Boolean,
  )(implicit ctx: Context) = {
    val isAuthor      = ctx.me.exists(_.id == article.author)
    val isMod         = isGranted(_.ArticleMod)
    val isAdmin       = isGranted(_.Admin)
    val isAuthorOrMod = isAuthor || isMod
    val editable      = (isAuthor && article.editable) || isMod
    val likesDisabled = !article.published || !ctx.isAuth

    val editButton = a(
      dataIcon := Icons.gear,
      cls      := "edit-link button text",
      href     := routes.Article.edit(article.id, langCode),
      disabled := !editable,
    )(trans.article.editArticle())

    views.html.base.layout(
      title = article.title(langCode),
      moreCss = cssTag("misc.article.show"),
      moreJs = frag(jsTag("misc.expand-text"), jsTag("misc.article-show")),
      openGraph = lila.app.ui
        .OpenGraph(
          `type` = "article",
          image = article.image(langCode),
          title = article.title(langCode),
          url =
            s"$netBaseUrl${routes.Article.show(article.id, langCode, article.slug(langCode)).url}",
          description = article.intro(langCode),
        )
        .some,
      csp = defaultCsp.withTwitter.some,
    )(
      main(cls := "page-menu page-small")(
        bits.menu(article.id),
        div(
          cls := List(
            "article page-menu__content box" -> true,
            "official" -> article.categories.contains(Article.Category.Official),
            "editable" -> editable,
            "no-likes" -> likesDisabled,
          ),
        )(
          isAuthorOrMod option
            div(cls := s"state-line state-${article.state.key}")(
              article.state match {
                case Article.State.Draft =>
                  frag(
                    p(trans.article.articleIsADraft()),
                    isAdmin option systemKeyForm(article),
                    stateButton(
                      article,
                      Article.State.ToPublish,
                      trans.article.submitForPublishing.txt(),
                    ),
                    editButton,
                  )
                case Article.State.ToPublish =>
                  frag(
                    p(trans.article.articleUnderReview()),
                    isAdmin option systemKeyForm(article),
                    stateButton(article, Article.State.Draft, trans.article.draft.txt()),
                    (isMod || isGranted(_.ArticlePublisher)) option frag(
                      isMod option st.input(name := "boost", tpe := "number", value := "0"),
                      stateButton(article, Article.State.Published, "Publish"),
                    ),
                    editButton,
                  )
                case _ =>
                  frag(
                    p(trans.article.published()),
                    isAdmin option systemKeyForm(article),
                    (isMod && !article.isSystemPage) option stateButton(
                      article,
                      Article.State.Draft,
                      "Unpublish",
                    ),
                    editButton,
                  )
              },
            ),
          article
            .image(langCode) map { img =>
            st.img(
              src := (urlOrImageStorageUrl(
                img,
                lila.common.ImageStorage.Imgproxy
                  .opts(
                    width = 1920,
                    height = 1080,
                    quality = 100,
                  )
                  .some,
              )),
            )
          },
          h1(lang := langCode)(article.title(langCode)),
          div(cls := "meta-headline")(
            div(cls := "meta")(
              span(cls := "text", dataIcon := Icons.person)(
                showUsernameById(article.author.some, withOnline = false),
              ),
              !article.isSystemPage option a(
                cls                := "text like-button",
                attr("data-liked") := liked.toString,
                attr("data-url")   := routes.Article.like(article.id),
                dataIcon           := (if (liked) Icons.heartFull else Icons.heartOutline),
              )(article.likes),
              span(cls := "text", dataIcon := Icons.clock)(
                semanticDate(article.publishedAt.getOrElse(article.updatedAt)),
              ),
              (ctx.isAuth && !article.isSystemPage) option a(
                href     := s"${routes.Report.form}?username=${article.author}",
                dataIcon := Icons.warning,
                title    := trans.reportXToModerators.txt(article.author),
              ),
              div(cls := "langs")(
                article.translations.toSeq.map { case (lc, c) =>
                  a(
                    cls  := s"lang-alt${(langCode == lc) ?? " selected"}",
                    href := s"${routes.Article.show(article.id, lc, c.slug)}",
                  )(lila.i18n.LangList.nameByStr(lc))
                },
                (isAuthorOrMod && article.translatable) option a(
                  dataIcon := Icons.createNew,
                  href     := routes.Article.translate(article.id, langCode),
                  title    := trans.article.translateArticle.txt(),
                  disabled := !editable,
                ),
              ),
            ),
          ),
          !article.isSystemPage option (
            if (isAuthorOrMod)
              div(cls := "chip-list", attr("data-url") := routes.Article.setCategories(article.id))(
                Article.Category.all.filter(c => c != Article.Category.Official || isMod) map { c =>
                  div(
                    cls                := s"chip${article.categories.contains(c) ?? " selected"}",
                    attr("data-categ") := c.key,
                  )(Article.Category.trans(c))
                },
              )
            else
              div(cls := "chip-list")(
                article.categories.map(c => div(cls := "chip")(Article.Category.trans(c))),
              )
          ),
          div(cls := "intro", lang := langCode)(
            article.intro(langCode),
          ),
          div(cls := "body expand-text", lang := langCode)(
            raw(rendered),
          ),
          !article.isSystemPage option div(cls := "footer")(
            div(cls := "buttons")(
              button(
                cls                := "text button button-red like-button",
                attr("data-liked") := liked.toString,
                attr("data-url")   := routes.Article.like(article.id),
                dataIcon           := (if (liked) Icons.heartFull else Icons.heartOutline),
              )(article.likes),
              a(
                href     := routes.Article.discuss(article.id),
                cls      := "button text discuss",
                dataIcon := Icons.talkAlt,
              )(
                trans.article.discuss(),
              ),
            ),
            a(href := routes.Plan.index)(
              trans.begForDonations(),
            ),
          ),
        ),
      ),
    )
  }

  private def stateButton(article: Article, state: Article.State, title: String) =
    postForm(action := routes.Article.setState(article.id))(
      submitButton(cls := "button")(title),
      form3.hidden("state", state.key),
    )

  private def systemKeyForm(article: Article) =
    postForm(action := routes.Article.setSystemKey(article.id))(
      st.input(name := "systemKey", value := ~article.systemKey),
      submitButton(cls := "button")("System key"),
    )
}
