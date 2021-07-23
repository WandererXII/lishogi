package lishogi.common

import akka.actor._

object lishogikka {

  val logger = lishogi.log("shutdown")

  def shutdown(cs: CoordinatedShutdown, makePhase: CoordinatedShutdown.type => String, name: String)(
      f: () => Funit
  ): Unit = {
    val phase = makePhase(CoordinatedShutdown)
    val msg   = s"$phase $name"
    cs.addTask(phase, name) { () =>
      logger.info(msg)
      Chronometer(f())
        .log(logger)(_ => msg)
        .result inject akka.Done
    }
  }
}
