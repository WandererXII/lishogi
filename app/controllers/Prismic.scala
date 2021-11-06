package controllers

import io.prismic.{ Api => PrismicApi, _ }
import lishogi.app._
import lishogi.common.BlogLangs

final class Prismic(
    env: Env
)(implicit ec: scala.concurrent.ExecutionContext, ws: play.api.libs.ws.WSClient) {

  private val logger = lishogi.log("prismic")

  private def prismicApi = env.blog.api.prismicApi

  implicit def makeLinkResolver(prismicApi: PrismicApi, ref: Option[String] = None) =
    DocumentLinkResolver(prismicApi) {
      case (link, _) => routes.Blog.show(link.id, link.slug, ref).url
      case _         => routes.Lobby.home().url
    }

  private def getDocument(id: String): Fu[Option[Document]] =
    prismicApi flatMap { api =>
      api
        .forms("everything")
        .query(s"""[[:d = at(document.id, "$id")]]""")
        .ref(api.master.ref)
        .submit() dmap {
        _.results.headOption
      }
    }

  private def getDocumentByUID(form: String, uid: String, langCode: String) =
    prismicApi flatMap { api =>
      api
        .forms("everything")
        .set("lang", BlogLangs.parse(langCode))
        .query(s"""[[:d = at(my.$form.uid, "$uid")]]""")
        .ref(api.master.ref)
        .submit() dmap {
        _.results.headOption
      }
    }

  def getPage(form: String, uid: String, langCode: String = BlogLangs.default) =
    prismicApi flatMap { api =>
      getDocumentByUID(form, uid, langCode) map2 { (doc: io.prismic.Document) =>
        doc -> makeLinkResolver(api)
      }
    } recover { case e: Exception =>
      logger.error(s"page:$uid", e)
      none
    }

  def getBookmark(name: String) =
    prismicApi flatMap { api =>
      api.bookmarks.get(name) ?? getDocument map2 { (doc: io.prismic.Document) =>
        doc -> makeLinkResolver(api)
      }
    } recover { case e: Exception =>
      logger.error(s"bookmark:$name", e)
      none
    }

  def getVariant(variant: shogi.variant.Variant) =
    prismicApi flatMap { api =>
      api
        .forms("variant")
        .query(s"""[[:d = at(my.variant.key, "${variant.key}")]]""")
        .ref(api.master.ref)
        .submit() map {
        _.results.headOption map (_ -> makeLinkResolver(api))
      }
    }
}
