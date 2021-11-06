package lishogi

package object team extends PackageObject {

  private[team] def logger = lishogi.log("team")

  type GameTeams = shogi.Color.Map[Team.ID]
}
