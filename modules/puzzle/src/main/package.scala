package lishogi

import lishogi.rating.Glicko

package object puzzle extends PackageObject {

  private[puzzle] def logger = lishogi.log("puzzle")
}

package puzzle {

  case class Result(win: Boolean) extends AnyVal {
    def loss   = !win
    def glicko = if (win) Glicko.Result.Win else Glicko.Result.Loss
  }
}
