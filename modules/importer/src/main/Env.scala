package lishogi.importer

import com.softwaremill.macwire._

@Module
final class Env(gameRepo: lishogi.game.GameRepo)(implicit ec: scala.concurrent.ExecutionContext) {

  lazy val forms = wire[DataForm]

  lazy val importer = wire[Importer]
}
