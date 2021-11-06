package lishogi.practice

import com.softwaremill.macwire._

import lishogi.common.config._

@Module
final class Env(
    configStoreApi: lishogi.memo.ConfigStore.Builder,
    studyApi: lishogi.study.StudyApi,
    cacheApi: lishogi.memo.CacheApi,
    db: lishogi.db.Db
)(implicit ec: scala.concurrent.ExecutionContext) {

  private lazy val coll = db(CollName("practice_progress"))

  import PracticeConfig.loader
  private lazy val configStore = configStoreApi[PracticeConfig]("practice", logger)

  lazy val api: PracticeApi = wire[PracticeApi]

  lishogi.common.Bus.subscribeFun("study") { case lishogi.study.actorApi.SaveStudy(study) =>
    api.structure onSave study
  }
}
