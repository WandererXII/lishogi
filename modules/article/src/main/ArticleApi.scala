package lila.article

import scala.concurrent.duration._

import play.api.i18n.Lang

import com.github.blemale.scaffeine.Cache
import org.joda.time.DateTime
import reactivemongo.api.bson._

import lila.db.dsl._
import lila.memo.Syncache
import lila.notify.Notification
import lila.user.User

final class ArticleApi(
    coll: Coll,
    cacheApi: lila.memo.CacheApi,
    notifyApi: lila.notify.NotifyApi,
    timeline: lila.hub.actors.Timeline,
)(implicit
    ec: scala.concurrent.ExecutionContext,
) {

  import Article.{ BSONFields => F }
  import BSONHandlers.Preview._
  import BSONHandlers._

  def byId(id: Article.ID): Fu[Option[Article]] = coll.byId[Article](id)

  def byIdWithLiked(id: Article.ID, userId: Option[User.ID]): Fu[Option[(Article, Boolean)]] =
    for {
      art <- byId(id)
      l   <- (userId ?? { u => liked(id, u) })
    } yield art.map(_ -> l)

  def bySystemKey(key: String) =
    coll.one[Article]($doc(F.systemKey -> key))

  def byBlogIdOrUid(idOrUid: String) =
    coll.one[Article]($doc(F.blogBcKey -> idOrUid))

  def countDraftsByAuthor(author: User.ID): Fu[Int] =
    coll.countSel(
      $doc(
        F.author -> author,
        F.state  -> Article.State.Draft.key,
      ),
    )

  def create(
      setup: ArticleForm.ContentSetup,
      userId: User.ID,
  ): Fu[Article.ID] = {
    val now    = DateTime.now
    val id     = lila.common.ThreadLocalRandom.nextString(16)
    val likers = List(userId)
    coll.update.one(
      $id(id),
      $set(
        F.author                        -> userId,
        F.state                         -> Article.State.Draft.key,
        F.likes                         -> likers.size,
        F.likers                        -> likers,
        F.createdAt                     -> now,
        F.updatedAt                     -> now,
        F.langs                         -> List(setup.langCode),
        s"${F.title}.${setup.langCode}" -> setup.title,
        s"${F.slug}.${setup.langCode}"  -> setup.slug,
        s"${F.image}.${setup.langCode}" -> ~setup.image,
        s"${F.intro}.${setup.langCode}" -> setup.intro,
        s"${F.body}.${setup.langCode}"  -> setup.body,
      ),
      upsert = true,
    ) inject id
  }

  def update(id: Article.ID, setup: ArticleForm.ContentSetup): Funit =
    coll.update
      .one(
        $id(id),
        $set(
          s"${F.title}.${setup.langCode}"      -> setup.title,
          s"${F.slug}.${setup.langCode}"       -> setup.slug,
          s"${F.image}.${setup.langCode}"      -> ~setup.image,
          s"${F.intro}.${setup.langCode}"      -> setup.intro,
          s"${F.body}.${setup.langCode}"       -> setup.body,
          s"${F.translator}.${setup.langCode}" -> ~setup.translator,
          F.updatedAt                          -> DateTime.now,
        ) ++ $addToSet(F.langs -> setup.langCode),
      )
      .void

  def setCategories(
      id: Article.ID,
      categories: List[Article.Category],
  ): Fu[List[Article.Category]] = {
    val c = categories.take(Article.Category.MaxPerArticle)
    coll.update
      .one(
        $id(id),
        $set(F.categories -> categories.map(_.key)),
      )
      .void inject c
  }

  def deleteLang(id: Article.ID, langCode: Article.Lang.Code): Funit =
    coll.update
      .one(
        $id(id),
        $unset(
          s"${F.title}.${langCode}",
          s"${F.slug}.${langCode}",
          s"${F.image}.${langCode}",
          s"${F.intro}.${langCode}",
          s"${F.body}.${langCode}",
          s"${F.translator}.${langCode}",
        ) ++ $pull(F.langs -> langCode),
      )
      .void

  def delete(id: Article.ID): Funit =
    coll.delete.one($id(id)).void

  def draft(id: Article.ID): Funit =
    coll.update
      .one(
        $id(id),
        $set(F.state -> Article.State.Draft.key),
      )
      .void

  def submitForPublishing(id: Article.ID): Funit =
    coll.update
      .one(
        $id(id),
        $set(F.state -> Article.State.ToPublish.key),
      )
      .void

  def publish(id: Article.ID, by: User.ID, rankBoost: Int): Funit =
    byId(id) flatMap {
      _ ?? { article =>
        (!article.published) ?? {
          val now = DateTime.now
          coll.update.one(
            $id(article.id),
            $set(
              F.state       -> Article.State.Published.key,
              F.publishedAt -> now,
              F.approvedBy  -> by,
              F.rankBoost   -> rankBoost,
              F.rank        -> Article.calculateRank(1 /* author */, now, rankBoost),
            ),
          ) >> notifyApi.addNotification(
            Notification.make(
              notifies = Notification.Notifies(article.author),
              content = lila.notify.ArticlePublished(article.id),
            ),
          ) >>- {
            if (article.publishedAt.isEmpty) {
              timeline ! {
                import lila.hub.actorApi.timeline.{ Propagate, ArticlePublished }
                Propagate(
                  ArticlePublished(article.author, article.id),
                ) toFollowersOf article.author
              }
            }
          }
        }
      }
    }

  def setSystemKey(id: Article.ID, key: String): Funit =
    if (key.isEmpty)
      coll.update
        .one(
          $id(id),
          $unset(F.systemKey),
        )
        .void
    else
      coll.update
        .one(
          $id(id),
          $set(
            F.systemKey -> key,
            F.state     -> Article.State.Published.key,
          ),
        )
        .void

  def liked(id: Article.ID, userId: User.ID): Fu[Boolean] =
    coll.exists($id(id) ++ $doc(F.likers -> userId))

  def like(id: Article.ID, userId: User.ID, v: Boolean): Fu[Int] =
    coll.update.one(
      $id(id),
      if (v) $addToSet(F.likers -> userId) else $pull(F.likers -> userId),
    ) >> {
      likesAndRank(id) flatMap {
        case None => fuccess(0)
        case Some((likes, rank)) =>
          coll.update.one(
            $id(id),
            $set(F.likes -> likes, F.rank -> rank),
          ) inject likes
      }
    }

  private def likesAndRank(id: Article.ID): Fu[Option[(Int, Article.Rank)]] =
    coll
      .aggregateWith[Bdoc]() { framework =>
        import framework._
        List(
          Match($id(id)),
          Project(
            $doc(
              "_id"         -> false,
              F.likes       -> $doc("$size" -> s"$$${F.likers}"),
              F.publishedAt -> true,
              F.rankBoost   -> true,
            ),
          ),
        )
      }
      .headOption
      .map { docOption =>
        for {
          doc         <- docOption
          likes       <- doc.getAsOpt[Int](F.likes)
          publishedAt <- doc.getAsOpt[DateTime](F.publishedAt)
          rankBoost = doc.getAsOpt[Int](F.rankBoost) | 0
        } yield (likes, Article.calculateRank(likes atLeast 1, publishedAt, rankBoost))
      }

  def renderMarkdown(a: Article, langCode: Article.Lang.Code): String =
    markdownCache.get(
      (a.id, langCode, a.updatedAt),
      _ => Markdown.render(a.body(langCode)),
    )

  private val markdownCache: Cache[(Article.ID, Article.Lang.Code, DateTime), String] =
    lila.memo.CacheApi.scaffeineNoScheduler
      .expireAfterWrite(5 minutes)
      .build[(Article.ID, Article.Lang.Code, DateTime), String]()

  def lobby(lang: Lang): List[Article.Preview] = {
    val l = Article.Lang.toLangCode(lang)
    if (l == lila.i18n.jaLang.language)
      lobbyCache.sync(l)
    else lobbyCache.sync("")
  }

  private val lobbyCache = cacheApi.sync[String, List[Article.Preview]](
    name = "article.lobby",
    initialCapacity = 2,
    compute = key => fetchForLobby(key),
    default = _ => Nil,
    strategy = Syncache.NeverWait,
    expireAfter = Syncache.NoExpire,
    refreshAfter = Syncache.RefreshAfterWrite(10 minutes),
  )

  private val maxShown = 3
  private def fetchForLobby(langKey: String): Fu[List[Article.Preview]] =
    coll
      .find(
        $doc(F.state -> Article.State.Published.key, F.systemKey $exists false)
          ++ langKey.some.filter(_.nonEmpty).?? { l =>
            $doc(F.langs -> l)
          },
      )
      .sort($sort desc F.rank)
      .cursor[Article.Preview]()
      .list(maxShown)

}
