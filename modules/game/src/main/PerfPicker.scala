package lishogi.game

import shogi.Speed
import lishogi.rating.{ Perf, PerfType }
import lishogi.user.Perfs

object PerfPicker {

  val default = (perfs: Perfs) => perfs.standard

  def perfType(speed: Speed, variant: shogi.variant.Variant, daysPerTurn: Option[Int]): Option[PerfType] =
    PerfType(key(speed, variant, daysPerTurn))

  def key(speed: Speed, variant: shogi.variant.Variant, daysPerTurn: Option[Int]): String =
    if (variant.standard) {
      if (daysPerTurn.isDefined || speed == Speed.Correspondence) PerfType.Correspondence.key
      else speed.key
    } else variant.key

  def key(game: Game): String = key(game.speed, game.ratingVariant, game.daysPerTurn)

  def main(speed: Speed, variant: shogi.variant.Variant, daysPerTurn: Option[Int]): Option[Perfs => Perf] =
    if (variant.standard) Some {
      if (daysPerTurn.isDefined) (perfs: Perfs) => perfs.correspondence
      else Perfs speedLens speed
    }
    else Perfs variantLens variant

  def main(game: Game): Option[Perfs => Perf] = main(game.speed, game.ratingVariant, game.daysPerTurn)

  def mainOrDefault(speed: Speed, variant: shogi.variant.Variant, daysPerTurn: Option[Int]): Perfs => Perf =
    main(speed, variant, daysPerTurn) orElse {
      (variant == shogi.variant.FromPosition) ?? main(speed, shogi.variant.Standard, daysPerTurn)
    } getOrElse default

  def mainOrDefault(game: Game): Perfs => Perf =
    mainOrDefault(game.speed, game.ratingVariant, game.daysPerTurn)
}
