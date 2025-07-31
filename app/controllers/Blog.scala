package controllers

import play.api.mvc._

import lila.api.Context
import lila.app._
import lila.blog.BlogApi
import lila.blog.BlogLang
import lila.common.config.MaxPerPage

final class Blog(
    env: Env,
) extends LilaController(env) {

  import controllers.Prismic.documentLinkResolver

  private def blogApi = env.blog.api

  def index(page: Int) =
    WithPrismic { implicit ctx => implicit prismic =>
      pageHit
      blogApi.recent(prismic.api, page, MaxPerPage(12), BlogLang.fromLang(ctx.lang)) flatMap {
        case Some(response) => fuccess(Ok(views.html.blog.index(response)))
        case _              => notFound
      }
    }

  def show(id: String) =
    WithPrismic { implicit ctx => implicit prismic =>
      pageHit
      blogApi.one(prismic, id) flatMap {
        case Some(post)
            if (post.isJapanese && ctx.isAnon && ctx.req.session.get("lang").isEmpty) => {
          val langCtx = ctx withLang lila.i18n.I18nLangPicker.byStr("ja-JP").getOrElse(ctx.lang)
          fuccess(Ok(views.html.blog.show(post)(langCtx, prismic)))
        }
        case Some(post) => fuccess(Ok(views.html.blog.show(post)))
        case _          => notFound
      }
    }

  def showBc(id: String, slug: String) =
    WithPrismic { implicit ctx => implicit prismic =>
      blogApi.one(prismic, id) flatMap {
        case Some(post) if post.doc.slugs.contains(slug) =>
          fuccess(MovedPermanently(routes.Blog.show(post.id).url))
        case _ => notFound
      }
    }

  def latest =
    WithPrismic { implicit ctx => implicit prismic =>
      blogApi.latest(prismic, BlogLang.fromLang(ctx.lang)) flatMap {
        case Some(post) =>
          fuccess(Redirect(routes.Blog.show(post.id)))
        case _ => notFound
      }
    }

  import scala.concurrent.duration._

  import lila.memo.CacheApi._

  private val atomCache = env.memo.cacheApi[BlogLang.Code, String](2, "blog.atom") {
    _.refreshAfterWrite(60.minutes)
      .buildAsyncFuture { langCode =>
        blogApi.masterContext flatMap { implicit prismic =>
          val blogLang = BlogLang.fromLangCode(langCode)
          blogApi.recent(prismic.api, 1, MaxPerPage(50), blogLang) map {
            _ ?? { docs =>
              views.html.blog.atom(docs, blogLang).render
            }
          }
        }
      }
  }

  def atom =
    Action.async { implicit req =>
      val blogLang = if (~get("lang", req) == "ja") BlogLang.Japanese else BlogLang.English
      atomCache.get(blogLang.code) map { xml =>
        Ok(xml) as XML
      }
    }

  private val sitemapCache = env.memo.cacheApi.unit[String] {
    _.refreshAfterWrite(6.hours)
      .buildAsyncFuture { _ =>
        blogApi.masterContext flatMap { implicit prismic =>
          blogApi.all() map {
            _.map { doc =>
              s"${env.net.baseUrl}${routes.Blog.show(doc.id)}"
            } mkString "\n"
          }
        }
      }
  }

  def sitemapTxt =
    Action.async {
      sitemapCache.getUnit map { txt =>
        Ok(txt) as TEXT
      }
    }

  def all =
    WithPrismic { implicit ctx => implicit prismic =>
      blogApi.byYear(prismic, lila.blog.thisYear, BlogLang.fromLang(ctx.lang)) map { posts =>
        Ok(views.html.blog.index.byYear(lila.blog.thisYear, posts))
      }
    }

  def year(year: Int) =
    WithPrismic { implicit ctx => implicit prismic =>
      if (lila.blog.allYears contains year)
        blogApi.byYear(prismic, year, BlogLang.fromLang(ctx.lang)) map { posts =>
          Ok(views.html.blog.index.byYear(year, posts))
        }
      else notFound
    }

  def discuss(id: String) =
    WithPrismic { _ => implicit prismic =>
      val categSlug = "general-shogi-discussion"
      val topicSlug = s"blog-$id"
      val redirect  = Redirect(routes.ForumTopic.show(categSlug, topicSlug))
      env.forum.topicRepo.existsByTree(categSlug, topicSlug) flatMap {
        case true => fuccess(redirect)
        case _ =>
          blogApi.one(prismic.api, id) flatMap {
            _ ?? { post =>
              env.forum.categRepo.bySlug(categSlug) flatMap {
                _ ?? { categ =>
                  env.forum.topicApi.makeBlogDiscuss(
                    categ = categ,
                    slug = topicSlug,
                    name = post.title,
                    url = s"${env.net.baseUrl}${routes.Blog.show(post.id)}",
                  )
                }
              } inject redirect
            }
          }
      }
    }

  private def WithPrismic(f: Context => BlogApi.Context => Fu[Result]): Action[Unit] =
    Open { ctx =>
      blogApi.context flatMap { prismic =>
        f(ctx)(prismic)
      }
    }

}
