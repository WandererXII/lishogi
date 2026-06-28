package lila.article

import scala.util.Success

import Article.{ BSONFields => F }
import org.joda.time.DateTime
import reactivemongo.api.bson._

import lila.db.BSON
import lila.db.dsl._

private object BSONHandlers {

  import Article._

  implicit private val stateHandler: BSONHandler[State] = quickHandler[State](
    { case BSONString(key) =>
      State.byKey(key).getOrElse(State.Draft)
    },
    x => BSONString(x.key),
  )

  implicit private val categoryHandler: BSONHandler[Category] = quickHandler[Category](
    { case BSONString(key) =>
      Category.byKey(key).getOrElse(Category.Culture)
    },
    x => BSONString(x.key),
  )

  private def readTranslationsMeta(r: BSON.Reader): Map[Lang.Code, Content.Meta] = {
    val titlesDoc = r.getO[Bdoc](F.title).getOrElse(BSONDocument.empty)
    val slugsDoc  = r.getO[Bdoc](F.slug).getOrElse(BSONDocument.empty)
    val imagesDoc = r.getO[Bdoc](F.image).getOrElse(BSONDocument.empty)

    r.strsD(F.langs)
      .flatMap { lang =>
        for {
          t <- titlesDoc.string(lang)
          s <- slugsDoc.string(lang)
          m = ~imagesDoc.string(lang)
        } yield lang ->
          Content.Meta(
            title = t,
            slug = s,
            image = m,
          )
      }
      .toMap
  }

  implicit private[article] val ArticleBSONReader: BSONDocumentReader[Article] =
    new BSONDocumentReader[Article] {
      def readDocument(doc: Bdoc) = {
        val r           = new BSON.Reader(doc)
        val metas       = readTranslationsMeta(r)
        val intros      = r.getD[Map[String, String]](F.intro)
        val bodies      = r.getD[Map[String, String]](F.body)
        val translators = r.getD[Map[String, String]](F.translator)
        val translations = metas map { case (lang, meta) =>
          lang -> Content(
            title = meta.title,
            slug = meta.slug,
            image = meta.image,
            intro = ~intros.get(lang),
            body = ~bodies.get(lang),
            translator = translators.get(lang),
          )
        }
        Success(
          Article(
            id = r.str(F.id),
            author = r.strD(F.author),
            translations = translations,
            categories = r.getD[List[Category]](F.categories),
            likes = r.intD(F.likes),
            state = r.getO[State](F.state) | State.Draft,
            approvedBy = r.strO(F.approvedBy),
            rankBoost = r.intD(F.rankBoost),
            createdAt = r.dateD(F.createdAt, DateTime.now),
            updatedAt = r.dateD(F.updatedAt, DateTime.now),
            publishedAt = r.dateO(F.publishedAt),
            systemKey = r.strO(F.systemKey),
          ),
        )
      }
    }

  object Preview {

    implicit val previewBSONReader: BSONDocumentReader[Article.Preview] =
      new BSONDocumentReader[Article.Preview] {

        def readDocument(doc: BSONDocument) = {
          val r = new BSON.Reader(doc)
          Success(
            Article.Preview(
              id = doc.string(F.id) err "Article id missing",
              author = ~doc.getAsOpt[String](F.author),
              translations = readTranslationsMeta(r),
              categories = r.getD[List[Category]](F.categories),
              state = doc.getAsOpt[Article.State](F.state) | Article.State.Draft,
              publishedAt = doc.getAsOpt[DateTime](F.publishedAt),
              updatedAt = doc.getAsOpt[DateTime](F.updatedAt) | DateTime.now,
              systemKey = doc.getAsOpt[String](F.systemKey),
            ),
          )
        }
      }

    val previewProjection = $doc(
      F.author      -> true,
      F.title       -> true,
      F.slug        -> true,
      F.image       -> true,
      F.categories  -> true,
      F.state       -> true,
      F.publishedAt -> true,
      F.updatedAt   -> true,
      F.langs       -> true,
      F.systemKey   -> true,
    )
  }

}
