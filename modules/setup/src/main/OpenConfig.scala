package lila.setup

import shogi.Clock
import shogi.format.forsyth.Sfen

import lila.game.PerfPicker
import lila.rating.PerfType

final case class OpenConfig(
    variant: shogi.variant.Variant,
    clock: Option[Clock.Config],
    days: Option[Int],
    rated: Boolean,
    sfen: Option[Sfen] = None,
    proMode: Boolean = false,
) {

  val strictSfen = false

  def >> = (variant.key.some, clock, days, rated, sfen.map(_.value), proMode.some).some

  def perfType: Option[PerfType] = PerfPicker.perfType(shogi.Speed(clock), variant, none)

  def validSfen =
    sfen.fold(true) { sf =>
      sf.toSituationPlus(variant)
        .exists(_.situation.playable(strict = strictSfen, withImpasse = true))
    }

}

object OpenConfig {

  def from(
      v: Option[String],
      cl: Option[Clock.Config],
      days: Option[Int],
      rated: Boolean,
      sf: Option[String],
      pm: Option[Boolean],
  ) =
    new OpenConfig(
      variant = shogi.variant.Variant.orDefault(~v),
      clock = cl.filter(c => c.limitSeconds > 0 || c.hasIncrement || c.hasByoyomi),
      days = days,
      rated = rated,
      sfen = sf map Sfen.clean,
      proMode = ~pm,
    )
}
