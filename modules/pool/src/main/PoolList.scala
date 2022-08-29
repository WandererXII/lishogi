package lila.pool

import play.api.libs.json.Json
import scala.concurrent.duration._

object PoolList {

  import PoolConfig._

  val all: List[PoolConfig] = List(
    PoolConfig(0 ++ (0, 5), Wave(12 seconds, 40 players)),
    PoolConfig(2 ++ (2, 0), Wave(18 seconds, 30 players)),
    PoolConfig(3 ++ (0, 5), Wave(12 seconds, 40 players)),
    PoolConfig(5 ++ (3, 0), Wave(22 seconds, 30 players)),
    PoolConfig(5 ++ (0, 10), Wave(14 seconds, 40 players)),
    PoolConfig(10 ++ (10, 0), Wave(25 seconds, 26 players)),
    PoolConfig(10 ++ (0, 30), Wave(13 seconds, 30 players)),
    PoolConfig(15 ++ (30, 0), Wave(20 seconds, 30 players)),
    PoolConfig(15 ++ (0, 60), Wave(30 seconds, 20 players)),
    PoolConfig(30 ++ (45, 0), Wave(40 seconds, 20 players)),
    PoolConfig(30 ++ (0, 60), Wave(60 seconds, 20 players))
  )

  val clockStringSet: Set[String] = all.view.map(_.clock.toString) to Set

  val json = Json toJson all

  implicit private class PimpedInt(self: Int) {
    def ++(increment: Int, byoyomi: Int) = shogi.Clock.Config(self * 60, increment, byoyomi, 1)
    def players            = NbPlayers(self)
  }
}
