package lishogi

package object swiss extends PackageObject {

  private[swiss] val logger = lishogi.log("swiss")

  private[swiss] type Ranking     = Map[lishogi.user.User.ID, Int]
  private[swiss] type RankingSwap = Map[Int, lishogi.user.User.ID]
}
