package controllers

import scala.annotation.nowarn

import play.api.libs.json._
import play.api.mvc._
import views._

import lila.api.Context
import lila.app._
import lila.article.{ Article => ArticleModel }
import lila.common.paginator.Paginator
import lila.common.paginator.PaginatorJson

final class Article(env: Env) extends LilaController(env) {

  private def api   = env.article.api
  private def forms = env.article.forms

  def default(page: Int) = recent("", page)

  def recent(lang: String, page: Int) =
    Open { implicit ctx =>
      val l = lang.some.filter(l =>
        l.nonEmpty && l != ArticleModel.Lang.allCode,
      ) orElse (ctx.isJapanese option ctx.lang.language)
      val categ = get("category").flatMap(ArticleModel.Category.byKey)
      env.article.pager.recent(
        l,
        categ,
        page,
      ) flatMap { pag =>
        negotiate(
          html = Ok(html.article.list.recent(pag, l, categ)).fuccess,
          json = apiArticles(pag),
        )
      }
    }

  def bestDefault(page: Int) = best("", page)

  def best(lang: String, page: Int) =
    Open { implicit ctx =>
      val l = lang.some.filter(l =>
        l.nonEmpty && l != ArticleModel.Lang.allCode,
      ) orElse (ctx.isJapanese option ctx.lang.language)
      val categ = get("category").flatMap(ArticleModel.Category.byKey)
      env.article.pager.best(
        l,
        categ,
        page,
      ) flatMap { pag =>
        negotiate(
          html = Ok(html.article.list.best(pag, l, categ)).fuccess,
          json = apiArticles(pag),
        )
      }
    }

  def mineLikes(page: Int) =
    Auth { implicit ctx => me =>
      env.article.pager.mineLikes(me, page) flatMap { pag =>
        negotiate(
          html = Ok(html.article.list.mineLikes(pag)).fuccess,
          json = apiArticles(pag),
        )
      }
    }

  def author(username: String, page: Int) = Open { implicit ctx =>
    env.user.repo.named(username).flatMap {
      _.fold(notFound(ctx)) { author =>
        env.article.pager.byAuthor(
          author,
          showAll = ctx.me.exists(_.id == author.id) || isGranted(_.ArticleMod),
          page,
        ) flatMap { pag =>
          negotiate(
            html = Ok(html.article.list.byAuthor(pag, author, ctx.me)).fuccess,
            json = apiArticles(pag),
          )
        }
      }
    }
  }

  def submitted(page: Int) = Open { implicit ctx =>
    env.article.pager.waitingForApproval(page) flatMap { pag =>
      negotiate(
        html = Ok(html.article.list.submitted(pag)).fuccess,
        json = apiArticles(pag),
      )
    }
  }

  def system(page: Int) = Auth { implicit ctx => _ =>
    if (isGranted(_.ArticleMod))
      env.article.pager.system(page) flatMap { pag =>
        negotiate(
          html = Ok(html.article.list.system(pag)).fuccess,
          json = apiArticles(pag),
        )
      }
    else notFound
  }

  private def MaxDrafts(res: => Fu[Result])(implicit ctx: Context): Fu[Result] =
    ctx.me ?? { me => api.countDraftsByAuthor(me.id) } flatMap { nb =>
      if (nb >= ArticleModel.maxDrafts)
        negotiate(
          html = BadRequest(views.html.site.message.tooManyDrafts(ArticleModel.maxDrafts)).fuccess,
          json = BadRequest(jsonError("You have too many unpublished drafts")).fuccess,
        )
      else res
    }

  def form = Auth { implicit ctx => _ =>
    NoLameOrBot {
      MaxDrafts(
        Ok(html.article.form.create(forms.create(ArticleModel.Lang.toLangCode(ctx.lang)))).fuccess,
      )
    }
  }

  def create =
    AuthBody { implicit ctx => implicit me =>
      NoLameOrBot {
        MaxDrafts {
          implicit val req = ctx.body
          forms.contentSetup
            .bindFromRequest()
            .fold(
              err => BadRequest(html.article.form.create(err)).fuccess,
              setup => {
                api.create(setup, me.id) map { id =>
                  Redirect(routes.Article.show(id, setup.langCode, setup.slug))
                }
              },
            )
        }
      }
    }

  def translate(id: String, lang: String) =
    Auth { implicit ctx => _ =>
      AsAuthorOrMod(id, editableOnly = true) { art =>
        (art.translations.get(lang) ?? { content =>
          Ok(html.article.form.translate(forms.translate(content), art, lang))
        }).fuccess
      }
    }

  def translateApply(id: String, lang: String) =
    AuthBody { implicit ctx => _ =>
      AsAuthorOrMod(id, editableOnly = true) { art =>
        implicit val req = ctx.body
        forms.contentSetup
          .bindFromRequest()
          .fold(
            err => BadRequest(html.article.form.translate(err, art, lang)).fuccess,
            setup =>
              api.update(id, setup) inject Redirect(
                routes.Article.show(id, setup.langCode, setup.slug),
              ),
          )
      }
    }

  def edit(id: String, lang: String) =
    Auth { implicit ctx => _ =>
      AsAuthorOrMod(id, editableOnly = true) { art =>
        (art.translations.get(lang) ?? { content =>
          Ok(html.article.form.edit(forms.edit(lang, content), art, lang))
        }).fuccess
      }
    }

  def editApply(id: String, lang: String) =
    AuthBody { implicit ctx => _ =>
      AsAuthorOrMod(id, editableOnly = true) { art =>
        implicit val req = ctx.body
        forms.contentSetup
          .bindFromRequest()
          .fold(
            err => BadRequest(html.article.form.edit(err, art, lang)).fuccess,
            setup =>
              api.update(id, setup) inject Redirect(
                routes.Article.show(id, setup.langCode, setup.slug),
              ),
          )
      }
    }

  def deleteArticle(id: String) =
    Auth { implicit ctx => _ =>
      AsAuthorOrMod(id) { art =>
        api.delete(art.id) >> negotiate(
          html = Redirect(routes.Article.author(art.author, 1)).flashSuccess.fuccess,
          json = jsonOkResult.fuccess,
        )
      }
    }

  def deleteLang(id: String, lang: String) =
    Auth { implicit ctx => _ =>
      AsAuthorOrMod(id) { art =>
        if (art.langCodes.size > 1)
          api.deleteLang(art.id, lang) >> negotiate(
            html = Redirect(routes.Article.showRedirect(id)).flashSuccess.fuccess,
            json = jsonOkResult.fuccess,
          )
        else BadRequest.fuccess
      }
    }

  def setState(id: String) =
    AuthBody { implicit ctx => me =>
      AsAuthorOrMod(id) { art =>
        val lang          = ArticleModel.Lang.resolve(art.langCodes, ctx.lang)
        implicit val body = ctx.body
        env.article.forms.state
          .bindFromRequest()
          .fold(
            _ => BadRequest.fuccess,
            stateKey => {
              val isMod = isGranted(_.ArticleMod)

              val actionOpt: Option[Funit] =
                if (stateKey == ArticleModel.State.Published.key && isMod)
                  Some(api.publish(id, me.id, (getInt("boost") | 0) atLeast 0))
                else if (
                  stateKey == ArticleModel.State.Published.key && isGranted(_.ArticlePublisher)
                )
                  Some(api.publish(id, me.id, 10))
                else if (stateKey == ArticleModel.State.ToPublish.key)
                  Some(api.submitForPublishing(id))
                else if (stateKey == ArticleModel.State.Draft.key && (!art.published || isMod))
                  Some(api.draft(id))
                else
                  None

              actionOpt match {
                case Some(action) =>
                  action >> Redirect(routes.Article.show(id, lang, art.slug(lang))).fuccess
                case None => Unauthorized.fuccess
              }
            },
          )
      }
    }
  def setCategories(id: String) =
    AuthBody { implicit ctx => _ =>
      AsAuthorOrMod(id, editableOnly = true) { art =>
        if (art.editable || isGranted(_.ArticleMod)) {
          val categs =
            get("categs").toList
              .flatMap(_.split(','))
              .flatMap(ArticleModel.Category.byKey)
              .filter(c => c != ArticleModel.Category.Official || isGranted(_.ArticleMod))
          JsonOk(api.setCategories(id, categs).map(_.map(_.key)))
        } else Unauthorized.fuccess
      }
    }

  def setSystemKey(id: String) =
    AuthBody { implicit ctx => _ =>
      if (isGranted(_.Admin)) {
        implicit val body = ctx.body
        env.article.forms.systemKey
          .bindFromRequest()
          .fold(
            _ => BadRequest.fuccess,
            systemKey => {
              api.setSystemKey(id, systemKey) >> Redirect(routes.Article.showRedirect(id)).fuccess
            },
          )
      } else Unauthorized.fuccess
    }

  def like(id: String) = Auth { implicit ctx => me =>
    JsonOk(api.like(id, me.id, getBool("v")))
  }

  def discuss(id: String) =
    Open { _ =>
      val categSlug = "articles"
      env.forum.topicRepo.byTree(categSlug, id) flatMap {
        case Some(topic) =>
          fuccess(
            Redirect(routes.ForumTopic.show(topic.categId, topic.slug)),
          )
        case _ =>
          api.byId(id) flatMap {
            case Some(art) if art.state == ArticleModel.State.Published =>
              env.forum.categRepo.bySlug(categSlug) flatMap {
                _ ?? { categ =>
                  env.forum.topicApi.makeArticleDiscuss(
                    categ = categ,
                    slug = art.id,
                    name = "IDK...",
                    url = s"${env.net.baseUrl}${routes.Article.showRedirect(id)}",
                  )
                }
              } inject Redirect(routes.ForumTopic.show(categSlug, art.id))
            case _ => NotFound.fuccess
          }
      }
    }

  def show(id: String, lang: String, @nowarn("cat=unused") slug: String) =
    Open { implicit ctx =>
      api.byIdWithLiked(id, ctx.userId) flatMap {
        case Some((art, liked))
            if (
              art.langCodes.contains(lang) &&
                ((art.state == ArticleModel.State.Published && !art.isSystemPage) || authorOrMod(
                  art,
                ))
            ) =>
          negotiate(
            html = Ok(html.article.show(art, api.renderMarkdown(art, lang), lang, liked)).fuccess,
            json = JsonOk(env.article.jsonView(art, lang, liked)).fuccess,
          )
        case _ => notFound
      }
    }

  def showRedirect(id: String) =
    Open { implicit ctx =>
      OptionFuRedirect(api.byId(id)) { art =>
        val lang = ArticleModel.Lang.resolve(art.langCodes, ctx.lang)
        fuccess(routes.Article.show(id, lang, art.slug(lang)))
      }
    }

  val thanks     = helpDocument("thanks")
  val contribute = helpDocument("contribute")
  val about      = helpDocument("about")
  val tos        = helpDocument("tos")
  val privacy    = helpDocument("privacy")
  val donations  = helpDocument("donations")

  private def helpDocument(key: String) =
    Open { implicit ctx =>
      pageHit
      OptionOk(api.bySystemKey(s"help-$key")) {
        case art => {
          val lang = ArticleModel.Lang.resolve(art.langCodes, ctx.lang)
          val r    = api.renderMarkdown(art, lang)
          views.html.site.help.article(key, art, r, lang)
        }
      }
    }

  def documentation(key: String) =
    Open { implicit ctx =>
      pageHit
      OptionOk(api.bySystemKey(s"doc-$key")) {
        case art => {
          val lang = ArticleModel.Lang.resolve(art.langCodes, ctx.lang)
          val r    = api.renderMarkdown(art, lang)
          views.html.site.documentation.article(key, art, r, lang)
        }
      }
    }

  def friendlySites =
    Open { implicit ctx =>
      pageHit
      OptionOk(api.bySystemKey("friendly-sites")) {
        case art => {
          views.html.site.help.friendlySites(api.renderMarkdown(art, ArticleModel.Lang.defaultCode))
        }
      }
    }

  def page(key: String) =
    Open { implicit ctx =>
      pageHit
      OptionOk(api.bySystemKey(s"page-${key}")) {
        case art => {
          val lang = ArticleModel.Lang.resolve(art.langCodes, ctx.lang)
          val r    = api.renderMarkdown(art, lang)
          views.html.site.page(key, art, r, lang)
        }
      }
    }

  def blogBc(idOrUid: String) =
    Open { implicit ctx =>
      OptionFuRedirect(api.byBlogIdOrUid(idOrUid)) { art =>
        val lang = ArticleModel.Lang.resolve(art.langCodes, ctx.lang)
        fuccess(routes.Article.show(art.id, lang, art.slug(lang)))
      }
    }

  private def authorOrMod(article: ArticleModel)(implicit ctx: Context): Boolean =
    ctx.userId.has(article.author) || isGranted(_.ArticleMod)

  private def AsAuthorOrMod(
      id: ArticleModel.ID,
      editableOnly: Boolean = false,
  )(f: ArticleModel => Fu[Result])(implicit ctx: Context): Fu[Result] =
    api.byId(id) flatMap {
      case None => notFound
      case Some(art)
          if authorOrMod(art) && (!editableOnly || art.editable || isGranted(_.ArticleMod)) =>
        f(art)
      case _ => fuccess(Unauthorized)
    }

  private def apiArticles(pager: Paginator[ArticleModel.Preview])(implicit ctx: Context) = {
    implicit val pagerWriter = Writes[ArticleModel.Preview] { p =>
      val lang = ArticleModel.Lang.resolve(p.langCodes, ctx.lang)
      env.article.jsonView.pagerData(p, lang)
    }
    Ok(
      Json.obj(
        "paginator" -> PaginatorJson(pager),
      ),
    ).fuccess
  }

}
