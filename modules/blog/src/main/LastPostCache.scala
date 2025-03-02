package lila.blog

import play.api.i18n.Lang

import lila.hub.actorApi.timeline.BlogPost
import lila.memo.CacheApi
import lila.memo.Syncache
import lila.timeline.EntryApi

final class LastPostCache(
    api: BlogApi,
    config: BlogConfig,
    timelineApi: EntryApi,
    prismic: lila.prismic.Prismic,
    cacheApi: CacheApi,
)(implicit ec: scala.concurrent.ExecutionContext) {

  private val cache = cacheApi.sync[BlogLang.Code, List[MiniPost]](
    name = "blog.lastPost",
    initialCapacity = 2,
    compute = langCode => fetch(BlogLang.fromLangCode(langCode)),
    default = _ => Nil,
    strategy = Syncache.NeverWait,
    expireAfter = Syncache.NoExpire,
    refreshAfter = Syncache.RefreshAfterWrite(config.lastPostTtl),
  )

  private def fetch(lang: BlogLang): Fu[List[MiniPost]] = {
    prismic.get flatMap { prismicApi =>
      api.recent(prismicApi, page = 1, lila.common.config.MaxPerPage(3), lang) map {
        _ ?? {
          _.currentPageResults.toList flatMap MiniPost.fromDocument(config.collection, "icon")
        }
      }
    }
  } addEffect maybeNotifyLastPost(lang)

  private var lastNotifiedId = none[String]

  // Blogs need to be published while server is up so the timeline entry can be inserted
  private def maybeNotifyLastPost(lang: BlogLang)(posts: List[MiniPost]): Unit =
    posts.headOption.filter(_ => lang == BlogLang.default) foreach { last =>
      if (lastNotifiedId.fold(false)(last.id !=)) timelineApi.broadcast.insert(BlogPost(last.id))
      lastNotifiedId = last.id.some
    }

  def apply(lang: BlogLang): List[MiniPost] = cache sync lang.code
  def apply(lang: Lang): List[MiniPost]     = apply(BlogLang.fromLang(lang))
}
