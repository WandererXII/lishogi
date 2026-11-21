package lila

import reactivemongo.api.ReadPreference

package object study extends PackageObject {

  private[study] val logger = lila.log("study")

  private[study] val readPref = ReadPreference.primary
}
