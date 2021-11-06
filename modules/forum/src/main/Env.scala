package lishogi.forum

import com.softwaremill.macwire._
import io.methvin.play.autoconfig._
import play.api.Configuration

import lishogi.common.config._
import lishogi.common.DetectLanguage
import lishogi.hub.actorApi.team.CreateTeam
import lishogi.mod.ModlogApi
import lishogi.notify.NotifyApi
import lishogi.relation.RelationApi

@Module
final private class ForumConfig(
    @ConfigName("topic.max_per_page") val topicMaxPerPage: MaxPerPage,
    @ConfigName("post.max_per_page") val postMaxPerPage: MaxPerPage,
    @ConfigName("public_categ_ids") val publicCategIds: List[String]
)

@Module
final class Env(
    appConfig: Configuration,
    db: lishogi.db.Db,
    modLog: ModlogApi,
    spam: lishogi.security.Spam,
    captcher: lishogi.hub.actors.Captcher,
    timeline: lishogi.hub.actors.Timeline,
    shutup: lishogi.hub.actors.Shutup,
    forumSearch: lishogi.hub.actors.ForumSearch,
    detectLanguage: DetectLanguage,
    notifyApi: NotifyApi,
    relationApi: RelationApi,
    userRepo: lishogi.user.UserRepo,
    cacheApi: lishogi.memo.CacheApi
)(implicit ec: scala.concurrent.ExecutionContext) {

  private val config = appConfig.get[ForumConfig]("forum")(AutoConfig.loader)

  lazy val categRepo = new CategRepo(db(CollName("f_categ")))
  lazy val topicRepo = new TopicRepo(db(CollName("f_topic")))
  lazy val postRepo  = new PostRepo(db(CollName("f_post")))

  lazy val categApi: CategApi = {
    val mk = (env: Env) => wire[CategApi]
    mk(this)
  }

  lazy val topicApi: TopicApi = {
    val mk = (max: MaxPerPage, env: Env) => wire[TopicApi]
    mk(config.topicMaxPerPage, this)
  }

  lazy val postApi: PostApi = {
    val mk = (max: MaxPerPage, env: Env) => wire[PostApi]
    mk(config.postMaxPerPage, this)
  }

  lazy val mentionNotifier: MentionNotifier = wire[MentionNotifier]
  lazy val forms                            = wire[DataForm]
  lazy val recent                           = wire[Recent]

  lishogi.common.Bus.subscribeFun("team", "gdprErase") {
    case CreateTeam(id, name, _)        => categApi.makeTeam(id, name)
    case lishogi.user.User.GDPRErase(user) => postApi erase user
  }
}
