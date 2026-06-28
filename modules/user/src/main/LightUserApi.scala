package lila.user

import scala.concurrent.duration._
import scala.util.Success

import reactivemongo.api.bson._

import lila.common.LightUser
import lila.db.dsl._
import lila.memo.CacheApi
import lila.memo.Syncache
import lila.user.User.{ BSONFields => F }

final class LightUserApi(
    repo: UserRepo,
    cacheApi: CacheApi,
)(implicit ec: scala.concurrent.ExecutionContext) {

  import LightUserApi._

  val async = new LightUser.Getter(cache.async)
  val sync  = new LightUser.GetterSync(cache.sync)

  def syncFallback(id: User.ID)  = sync(id) | LightUser.fallback(id)
  def asyncFallback(id: User.ID) = async(id) dmap (_ | LightUser.fallback(id))

  def asyncMany = cache.asyncMany _

  def asyncManyFallback(ids: Seq[User.ID]): Fu[Seq[LightUser]] =
    ids.map(asyncFallback).sequenceFu

  def invalidate = cache invalidate _

  def preloadOne                     = cache preloadOne _
  def preloadMany                    = cache preloadMany _
  def preloadUser(user: User)        = cache.set(user.id, user.light.some)
  def preloadUsers(users: Seq[User]) = users.foreach(preloadUser)

  private val cache = cacheApi.sync[User.ID, Option[LightUser]](
    name = "user.light",
    initialCapacity = 4096,
    compute = id => repo.coll.one[LightUser]($id(id), projection.some),
    default = id => LightUser(id, id, none, false, none).some,
    strategy = Syncache.WaitAfterUptime(8 millis),
    expireAfter = Syncache.ExpireAfterWrite(20 minutes),
  )
}

private object LightUserApi {

  implicit val lightUserBSONReader: BSONDocumentReader[LightUser] =
    new BSONDocumentReader[LightUser] {

      def readDocument(doc: BSONDocument) =
        Success(
          LightUser(
            id = doc.string(F.id) err "LightUser id missing",
            name = doc.string(F.username) err "LightUser username missing",
            title = doc.string(F.title),
            isPatron = ~doc.child(F.plan).flatMap(_.getAsOpt[Boolean]("active")),
            countryCode = doc
              .child(F.profile)
              .flatMap(_.getAsOpt[String]("country"))
              .flatMap(Countries.info)
              .map(_.code),
          ),
        )
    }

  val projection = $doc(
    F.username              -> true,
    F.title                 -> true,
    s"${F.plan}.active"     -> true,
    s"${F.profile}.country" -> true,
  )
}
