package lishogi

package object relay extends PackageObject {

  private[relay] val logger = lishogi.log("relay")

  private[relay] type RelayGames = Vector[RelayGame]
}
