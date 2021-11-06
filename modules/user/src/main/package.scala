package lishogi

package object user extends PackageObject {

  private[user] def logger = lishogi.log("user")

  type Trophies = List[Trophy]
}
