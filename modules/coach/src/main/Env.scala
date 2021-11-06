package lishogi.coach

import com.softwaremill.macwire._
import io.methvin.play.autoconfig._
import play.api.Configuration

import lishogi.common.config._
import lishogi.security.Permission

@Module
final private class CoachConfig(
    @ConfigName("collection.coach") val coachColl: CollName,
    @ConfigName("collection.review") val reviewColl: CollName
)

@Module
final class Env(
    appConfig: Configuration,
    userRepo: lishogi.user.UserRepo,
    notifyApi: lishogi.notify.NotifyApi,
    cacheApi: lishogi.memo.CacheApi,
    db: lishogi.db.Db,
    imageRepo: lishogi.db.ImageRepo
)(implicit ec: scala.concurrent.ExecutionContext) {

  private val config = appConfig.get[CoachConfig]("coach")(AutoConfig.loader)

  private lazy val coachColl = db(config.coachColl)

  private lazy val photographer = new lishogi.db.Photographer(imageRepo, "coach")

  lazy val api = new CoachApi(
    coachColl = coachColl,
    userRepo = userRepo,
    reviewColl = db(config.reviewColl),
    photographer = photographer,
    notifyApi = notifyApi,
    cacheApi = cacheApi
  )

  lazy val pager = wire[CoachPager]

  lishogi.common.Bus.subscribeFun("adjustCheater", "finishGame", "shadowban", "setPermissions") {
    case lishogi.hub.actorApi.mod.Shadowban(userId, true) =>
      api.toggleApproved(userId, false)
      api.reviews deleteAllBy userId
    case lishogi.hub.actorApi.mod.MarkCheater(userId, true) =>
      api.toggleApproved(userId, false)
      api.reviews deleteAllBy userId
    case lishogi.hub.actorApi.mod.SetPermissions(userId, permissions) =>
      api.toggleApproved(userId, permissions.has(Permission.Coach.dbKey))
    case lishogi.game.actorApi.FinishGame(game, sente, gote) if game.rated =>
      if (game.perfType.exists(lishogi.rating.PerfType.standard.contains)) {
        sente ?? api.setRating
        gote ?? api.setRating
      }
    case lishogi.user.User.GDPRErase(user) => api.reviews deleteAllBy user.id
  }

  def cli =
    new lishogi.common.Cli {
      def process = {
        case "coach" :: "enable" :: username :: Nil  => api.toggleApproved(username, true)
        case "coach" :: "disable" :: username :: Nil => api.toggleApproved(username, false)
      }
    }
}
