package lila.article

import lila.common.paginator.Paginator
import lila.db.dsl._
import lila.db.paginator.Adapter
import lila.user.User

final class ArticlePager(
    coll: Coll,
)(implicit ec: scala.concurrent.ExecutionContext) {

  val maxPerPage = lila.common.config.MaxPerPage(10)

  import Article.{ BSONFields => F }
  import BSONHandlers.Preview._

  def recent(langCode: Option[Article.Lang.Code], category: Option[Article.Category], page: Int) =
    paginator(
      ($doc(F.state -> Article.State.Published.key, F.systemKey $exists false) ++ langCode.?? { l =>
        $doc(F.langs -> l)
      }) ++ category.?? { c => $doc(F.categories -> c.key) },
      page,
    )

  def best(langCode: Option[Article.Lang.Code], category: Option[Article.Category], page: Int) =
    paginator(
      ($doc(F.state -> Article.State.Published.key, F.systemKey $exists false) ++ langCode.?? { l =>
        $doc(F.langs -> l)
      }) ++ category.?? { c => $doc(F.categories -> c.key) },
      page,
      F.likes.some,
    )

  def byAuthor(author: User, showAll: Boolean, page: Int) =
    paginator(
      $doc(F.author -> author.id, F.systemKey $exists false) ++ {
        !showAll ?? $doc(F.state -> Article.State.Published.key)
      },
      page,
      showAll option F.updatedAt,
    )

  def mineLikes(me: User, page: Int) =
    paginator(
      $doc(
        F.likers -> me.id,
        F.state  -> Article.State.Published.key,
        F.author $ne me.id,
        F.systemKey $exists false,
      ),
      page,
    )

  def waitingForApproval(page: Int) =
    paginator(
      $doc(F.state -> Article.State.ToPublish.key, F.systemKey $exists false),
      page,
      F.updatedAt.some,
    )

  def system(page: Int) =
    paginator(
      $doc(F.systemKey $exists true),
      page,
      F.updatedAt.some,
    )

  private def paginator(
      selector: Bdoc,
      page: Int,
      sort: Option[String] = none,
  ): Fu[Paginator[Article.Preview]] = {
    val adapter = new Adapter[Article.Preview](
      collection = coll,
      selector = selector,
      projection = previewProjection.some,
      sort = $sort desc (sort | F.publishedAt),
    )
    Paginator(
      adapter = adapter,
      currentPage = page,
      maxPerPage = maxPerPage,
    )
  }

}
