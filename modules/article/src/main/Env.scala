package lila.article

import com.softwaremill.macwire._

import lila.common.config._

@Module
final class Env(
    db: lila.db.Db,
    cacheApi: lila.memo.CacheApi,
    lightUserApi: lila.user.LightUserApi,
    notifyApi: lila.notify.NotifyApi,
    timeline: lila.hub.actors.Timeline,
)(implicit
    ec: scala.concurrent.ExecutionContext,
) {

  private lazy val articleColl = db(CollName("articles"))

  lazy val api      = wire[ArticleApi]
  lazy val forms    = ArticleForm
  lazy val jsonView = wire[JsonView]
  lazy val pager    = wire[ArticlePager]

}
