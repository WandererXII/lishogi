package lila.setup

import org.specs2.mutable.Specification

import shogi.Gote
import shogi.format.forsyth.Sfen
import shogi.variant.Standard

class ConfigTest extends Specification {

  "positional game clocks" should {
    "start with gote for a gote-to-move position" in {
      val config = AiConfig(
        variant = Standard,
        timeMode = TimeMode.RealTime,
        time = 10,
        increment = 0,
        byoyomi = 30,
        periods = 1,
        days = 1,
        level = 1,
        color = lila.lobby.Color.Sente,
        sfen = Some(Sfen(Standard.initialSfen.value.replace(" b ", " w "))),
      )
      val game = config.game(None)
      game.turnColor must_== Gote
      game.clock.map(_.color) must_== Some(Gote)
    }
  }
}
