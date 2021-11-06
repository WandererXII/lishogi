package lishogi.pool

import scala.concurrent.duration._

import lishogi.rating.PerfType

case class PoolConfig(
    clock: shogi.Clock.Config,
    wave: PoolConfig.Wave
) {

  val perfType = PerfType(shogi.Speed(clock).key) | PerfType.Classical

  val id = PoolConfig clockToId clock
}

object PoolConfig {

  case class Id(value: String)     extends AnyVal
  case class NbPlayers(value: Int) extends AnyVal

  case class Wave(every: FiniteDuration, players: NbPlayers)

  def clockToId(clock: shogi.Clock.Config) = Id(clock.show)

  import play.api.libs.json._
  implicit val poolConfigJsonWriter = OWrites[PoolConfig] { p =>
    Json.obj(
      "id"   -> p.id.value,
      "lim"  -> p.clock.limitInMinutes,
      "inc"  -> p.clock.incrementSeconds,
      "byo"  -> p.clock.byoyomiSeconds,
      "per"  -> p.clock.periods,
      "perf" -> p.perfType.trans(lishogi.i18n.defaultLang)
    )
  }
}
