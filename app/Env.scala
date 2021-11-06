package lishogi.app

import akka.actor._
import com.softwaremill.macwire._
import lishogi.memo.SettingStore.Strings._
import play.api.libs.ws.WSClient
import play.api.mvc.{ ControllerComponents, SessionCookieBaker }
import play.api.{ Configuration, Environment, Mode }
import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }

import lishogi.common.config._
import lishogi.common.{ Bus, Lishogikka, Strings }

final class Env(
    val config: Configuration,
    val common: lishogi.common.Env,
    val imageRepo: lishogi.db.ImageRepo,
    val api: lishogi.api.Env,
    val user: lishogi.user.Env,
    val security: lishogi.security.Env,
    val hub: lishogi.hub.Env,
    val socket: lishogi.socket.Env,
    val memo: lishogi.memo.Env,
    val msg: lishogi.msg.Env,
    val game: lishogi.game.Env,
    val bookmark: lishogi.bookmark.Env,
    val search: lishogi.search.Env,
    val gameSearch: lishogi.gameSearch.Env,
    val timeline: lishogi.timeline.Env,
    val forum: lishogi.forum.Env,
    val forumSearch: lishogi.forumSearch.Env,
    val team: lishogi.team.Env,
    val teamSearch: lishogi.teamSearch.Env,
    val analyse: lishogi.analyse.Env,
    val mod: lishogi.mod.Env,
    val notifyM: lishogi.notify.Env,
    val round: lishogi.round.Env,
    val lobby: lishogi.lobby.Env,
    val setup: lishogi.setup.Env,
    val importer: lishogi.importer.Env,
    val tournament: lishogi.tournament.Env,
    val simul: lishogi.simul.Env,
    val relation: lishogi.relation.Env,
    val report: lishogi.report.Env,
    val appeal: lishogi.appeal.Env,
    val pref: lishogi.pref.Env,
    val chat: lishogi.chat.Env,
    val puzzle: lishogi.puzzle.Env,
    val coordinate: lishogi.coordinate.Env,
    val tv: lishogi.tv.Env,
    val blog: lishogi.blog.Env,
    val history: lishogi.history.Env,
    val video: lishogi.video.Env,
    val playban: lishogi.playban.Env,
    val shutup: lishogi.shutup.Env,
    val insight: lishogi.insight.Env,
    val push: lishogi.push.Env,
    val perfStat: lishogi.perfStat.Env,
    val slack: lishogi.slack.Env,
    val challenge: lishogi.challenge.Env,
    val explorer: lishogi.explorer.Env,
    val fishnet: lishogi.fishnet.Env,
    val study: lishogi.study.Env,
    val studySearch: lishogi.studySearch.Env,
    val learn: lishogi.learn.Env,
    val plan: lishogi.plan.Env,
    val event: lishogi.event.Env,
    val coach: lishogi.coach.Env,
    val clas: lishogi.clas.Env,
    val pool: lishogi.pool.Env,
    val practice: lishogi.practice.Env,
    val irwin: lishogi.irwin.Env,
    val activity: lishogi.activity.Env,
    val relay: lishogi.relay.Env,
    val streamer: lishogi.streamer.Env,
    val oAuth: lishogi.oauth.Env,
    val bot: lishogi.bot.Env,
    val evalCache: lishogi.evalCache.Env,
    val rating: lishogi.rating.Env,
    val swiss: lishogi.swiss.Env,
    val storm: lishogi.storm.Env,
    val lishogiCookie: lishogi.common.LishogiCookie,
    val controllerComponents: ControllerComponents
)(implicit
    val system: ActorSystem,
    val executionContext: ExecutionContext,
    val mode: play.api.Mode
) {

  def net = common.netConfig

  val isProd            = mode == Mode.Prod && net.isProd
  val isDev             = mode == Mode.Dev
  val isStage           = mode == Mode.Prod && !net.isProd
  val explorerEndpoint  = config.get[String]("explorer.endpoint")
  val tablebaseEndpoint = config.get[String]("explorer.tablebase.endpoint")

  val appVersionDate    = config.getOptional[String]("app.version.date")
  val appVersionCommit  = config.getOptional[String]("app.version.commit")
  val appVersionMessage = config.getOptional[String]("app.version.message")

  lazy val apiTimelineSetting = memo.settingStore[Int](
    "apiTimelineEntries",
    default = 10,
    text = "API timeline entries to serve".some
  )
  lazy val noDelaySecretSetting = memo.settingStore[Strings](
    "noDelaySecrets",
    default = Strings(Nil),
    text =
      "Secret tokens that allows fetching ongoing games without the 3-moves delay. Separated by commas.".some
  )
  lazy val featuredTeamsSetting = memo.settingStore[Strings](
    "featuredTeams",
    default = Strings(Nil),
    text = "Team IDs that always get their tournaments visible on /tournament".some
  )

  lazy val preloader     = wire[mashup.Preload]
  lazy val socialInfo    = wire[mashup.UserInfo.SocialApi]
  lazy val userNbGames   = wire[mashup.UserInfo.NbGamesApi]
  lazy val userInfo      = wire[mashup.UserInfo.UserInfoApi]
  lazy val teamInfo      = wire[mashup.TeamInfoApi]
  lazy val gamePaginator = wire[mashup.GameFilterMenu.PaginatorBuilder]
  lazy val pageCache     = wire[http.PageCache]

  private val tryDailyPuzzle: lishogi.puzzle.DailyPuzzle.Try = () =>
    Future {
      puzzle.daily.get
    }.flatMap(identity)
      .withTimeoutDefault(50.millis, none) recover { case e: Exception =>
      lishogi.log("preloader").warn("daily puzzle", e)
      none
    }

  def scheduler = system.scheduler

  def closeAccount(userId: lishogi.user.User.ID, self: Boolean): Funit =
    for {
      u <- user.repo byId userId orFail s"No such user $userId"
      badApple = u.lameOrTrollOrAlt
      playbanned <- playban.api.hasCurrentBan(u.id)
      _          <- user.repo.disable(u, keepEmail = badApple || playbanned)
      _          <- relation.api.unfollowAll(u.id)
      _          <- user.rankingApi.remove(u.id)
      _          <- team.api.quitAll(u.id)
      _          <- challenge.api.removeByUserId(u.id)
      _          <- tournament.api.withdrawAll(u)
      _          <- plan.api.cancel(u).nevermind
      _          <- lobby.seekApi.removeByUser(u)
      _          <- security.store.closeAllSessionsOf(u.id)
      _          <- push.webSubscriptionApi.unsubscribeByUser(u)
      _          <- streamer.api.demote(u.id)
      _          <- coach.api.remove(u.id)
      reports    <- report.api.processAndGetBySuspect(lishogi.report.Suspect(u))
      _          <- self ?? mod.logApi.selfCloseAccount(u.id, reports)
      _ <- u.marks.troll ?? relation.api.fetchFollowing(u.id) flatMap {
        activity.write.unfollowAll(u, _)
      }
    } yield Bus.publish(lishogi.hub.actorApi.security.CloseAccount(u.id), "accountClose")

  Bus.subscribeFun("garbageCollect") { case lishogi.hub.actorApi.security.GarbageCollect(userId) =>
    // GC can be aborted by reverting the initial SB mark
    user.repo.isTroll(userId) foreach { troll =>
      if (troll) scheduler.scheduleOnce(1.second) {
        closeAccount(userId, self = false)
      }
    }
  }

  system.actorOf(Props(new actor.Renderer), name = config.get[String]("app.renderer.name"))
}

final class EnvBoot(
    config: Configuration,
    environment: Environment,
    controllerComponents: ControllerComponents,
    cookieBacker: SessionCookieBaker,
    shutdown: CoordinatedShutdown
)(implicit
    ec: ExecutionContext,
    system: ActorSystem,
    ws: WSClient
) {

  implicit def scheduler   = system.scheduler
  implicit def mode        = environment.mode
  def appPath              = AppPath(environment.rootPath)
  def baseUrl              = common.netConfig.baseUrl
  implicit def idGenerator = game.idGenerator

  lazy val mainDb: lishogi.db.Db = mongo.blockingDb("main", config.get[String]("mongodb.uri"))
  lazy val imageRepo          = new lishogi.db.ImageRepo(mainDb(CollName("image")))

  // wire all the lishogi modules
  lazy val common: lishogi.common.Env           = wire[lishogi.common.Env]
  lazy val memo: lishogi.memo.Env               = wire[lishogi.memo.Env]
  lazy val mongo: lishogi.db.Env                = wire[lishogi.db.Env]
  lazy val user: lishogi.user.Env               = wire[lishogi.user.Env]
  lazy val security: lishogi.security.Env       = wire[lishogi.security.Env]
  lazy val hub: lishogi.hub.Env                 = wire[lishogi.hub.Env]
  lazy val socket: lishogi.socket.Env           = wire[lishogi.socket.Env]
  lazy val msg: lishogi.msg.Env                 = wire[lishogi.msg.Env]
  lazy val game: lishogi.game.Env               = wire[lishogi.game.Env]
  lazy val bookmark: lishogi.bookmark.Env       = wire[lishogi.bookmark.Env]
  lazy val search: lishogi.search.Env           = wire[lishogi.search.Env]
  lazy val gameSearch: lishogi.gameSearch.Env   = wire[lishogi.gameSearch.Env]
  lazy val timeline: lishogi.timeline.Env       = wire[lishogi.timeline.Env]
  lazy val forum: lishogi.forum.Env             = wire[lishogi.forum.Env]
  lazy val forumSearch: lishogi.forumSearch.Env = wire[lishogi.forumSearch.Env]
  lazy val team: lishogi.team.Env               = wire[lishogi.team.Env]
  lazy val teamSearch: lishogi.teamSearch.Env   = wire[lishogi.teamSearch.Env]
  lazy val analyse: lishogi.analyse.Env         = wire[lishogi.analyse.Env]
  lazy val mod: lishogi.mod.Env                 = wire[lishogi.mod.Env]
  lazy val notifyM: lishogi.notify.Env          = wire[lishogi.notify.Env]
  lazy val round: lishogi.round.Env             = wire[lishogi.round.Env]
  lazy val lobby: lishogi.lobby.Env             = wire[lishogi.lobby.Env]
  lazy val setup: lishogi.setup.Env             = wire[lishogi.setup.Env]
  lazy val importer: lishogi.importer.Env       = wire[lishogi.importer.Env]
  lazy val tournament: lishogi.tournament.Env   = wire[lishogi.tournament.Env]
  lazy val simul: lishogi.simul.Env             = wire[lishogi.simul.Env]
  lazy val relation: lishogi.relation.Env       = wire[lishogi.relation.Env]
  lazy val report: lishogi.report.Env           = wire[lishogi.report.Env]
  lazy val appeal: lishogi.appeal.Env           = wire[lishogi.appeal.Env]
  lazy val pref: lishogi.pref.Env               = wire[lishogi.pref.Env]
  lazy val chat: lishogi.chat.Env               = wire[lishogi.chat.Env]
  lazy val puzzle: lishogi.puzzle.Env           = wire[lishogi.puzzle.Env]
  lazy val coordinate: lishogi.coordinate.Env   = wire[lishogi.coordinate.Env]
  lazy val tv: lishogi.tv.Env                   = wire[lishogi.tv.Env]
  lazy val blog: lishogi.blog.Env               = wire[lishogi.blog.Env]
  lazy val history: lishogi.history.Env         = wire[lishogi.history.Env]
  lazy val video: lishogi.video.Env             = wire[lishogi.video.Env]
  lazy val playban: lishogi.playban.Env         = wire[lishogi.playban.Env]
  lazy val shutup: lishogi.shutup.Env           = wire[lishogi.shutup.Env]
  lazy val insight: lishogi.insight.Env         = wire[lishogi.insight.Env]
  lazy val push: lishogi.push.Env               = wire[lishogi.push.Env]
  lazy val perfStat: lishogi.perfStat.Env       = wire[lishogi.perfStat.Env]
  lazy val slack: lishogi.slack.Env             = wire[lishogi.slack.Env]
  lazy val challenge: lishogi.challenge.Env     = wire[lishogi.challenge.Env]
  lazy val explorer: lishogi.explorer.Env       = wire[lishogi.explorer.Env]
  lazy val fishnet: lishogi.fishnet.Env         = wire[lishogi.fishnet.Env]
  lazy val study: lishogi.study.Env             = wire[lishogi.study.Env]
  lazy val studySearch: lishogi.studySearch.Env = wire[lishogi.studySearch.Env]
  lazy val learn: lishogi.learn.Env             = wire[lishogi.learn.Env]
  lazy val plan: lishogi.plan.Env               = wire[lishogi.plan.Env]
  lazy val event: lishogi.event.Env             = wire[lishogi.event.Env]
  lazy val coach: lishogi.coach.Env             = wire[lishogi.coach.Env]
  lazy val clas: lishogi.clas.Env               = wire[lishogi.clas.Env]
  lazy val pool: lishogi.pool.Env               = wire[lishogi.pool.Env]
  lazy val practice: lishogi.practice.Env       = wire[lishogi.practice.Env]
  lazy val irwin: lishogi.irwin.Env             = wire[lishogi.irwin.Env]
  lazy val activity: lishogi.activity.Env       = wire[lishogi.activity.Env]
  lazy val relay: lishogi.relay.Env             = wire[lishogi.relay.Env]
  lazy val streamer: lishogi.streamer.Env       = wire[lishogi.streamer.Env]
  lazy val oAuth: lishogi.oauth.Env             = wire[lishogi.oauth.Env]
  lazy val bot: lishogi.bot.Env                 = wire[lishogi.bot.Env]
  lazy val evalCache: lishogi.evalCache.Env     = wire[lishogi.evalCache.Env]
  lazy val rating: lishogi.rating.Env           = wire[lishogi.rating.Env]
  lazy val swiss: lishogi.swiss.Env             = wire[lishogi.swiss.Env]
  lazy val storm: lishogi.storm.Env             = wire[lishogi.storm.Env]
  lazy val api: lishogi.api.Env                 = wire[lishogi.api.Env]
  lazy val lishogiCookie                        = wire[lishogi.common.LishogiCookie]

  val env: lishogi.app.Env = {
    val c = lishogi.common.Chronometer.sync(wire[lishogi.app.Env])
    lishogi.log("boot").info(s"Loaded lishogi modules in ${c.showDuration}")
    c.result
  }

  templating.Environment setEnv env

  // free memory for reload workflow
  if (env.isDev)
    Lishogikka.shutdown(shutdown, _.PhaseServiceStop, "Freeing dev memory") { () =>
      Future {
        templating.Environment.destroy()
        lishogi.common.Bus.destroy()
        lishogi.mon.destroy()
      }
    }
}
