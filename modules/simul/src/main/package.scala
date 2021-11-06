package lishogi

package object simul extends PackageObject {

  private[simul] val logger = lishogi.log("simul")

}

package simul {
  case class SimulTeam(id: String, name: String, isIn: Boolean)
}
