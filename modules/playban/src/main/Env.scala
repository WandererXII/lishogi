package lishogi.playban

import com.softwaremill.macwire._
import play.api.Configuration

import lishogi.common.config.CollName

@Module
final class Env(
    appConfig: Configuration,
    messenger: lishogi.msg.MsgApi,
    chatApi: lishogi.chat.ChatApi,
    userRepo: lishogi.user.UserRepo,
    lightUser: lishogi.common.LightUser.Getter,
    db: lishogi.db.Db,
    cacheApi: lishogi.memo.CacheApi
)(implicit ec: scala.concurrent.ExecutionContext) {

  private lazy val playbanColl = db(
    CollName(appConfig.get[String]("playban.collection.playban"))
  )

  private lazy val feedback = wire[PlaybanFeedback]

  private lazy val sandbag = wire[SandbagWatch]

  lazy val api = wire[PlaybanApi]
}
