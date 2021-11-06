package lishogi.blog

import io.prismic.Document

import lishogi.hub.actorApi.timeline.BlogPost
import lishogi.timeline.EntryApi

final private[blog] class Notifier(
    blogApi: BlogApi,
    timelineApi: EntryApi
)(implicit ec: scala.concurrent.ExecutionContext) {

  def apply(id: String, langCode: String): Funit =
    blogApi.prismicApi flatMap { prismicApi =>
      blogApi.one(prismicApi, none, id) orFail
        s"No such document: $id" flatMap doSendWithLang(langCode)
    }
  private def doSendWithLang(langCode: String)(post: Document) = doSend(post, langCode)

  private def doSend(post: Document, langCode: String): Funit =
    post.getText("blog.title") ?? { title =>
      timelineApi.broadcast.insert {
        BlogPost(id = post.id, slug = post.slug, title = title, langCode = langCode)
      }
    }
}
