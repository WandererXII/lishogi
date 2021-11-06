package lishogi

import reactivemongo.api.ReadPreference

package object study extends PackageObject {

  private[study] val logger = lishogi.log("study")

  private[study] type ChapterMap = Map[lishogi.study.Chapter.Id, lishogi.study.Chapter]

  private[study] val readPref = ReadPreference.primary
}
