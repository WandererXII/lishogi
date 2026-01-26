package lila.rating

import play.api.i18n.Lang

import lila.common.Icons
import lila.i18n.I18nKeys

sealed abstract class PerfType(
    val id: Perf.ID,
    val key: Perf.Key,
    val icon: String,
) {

  def trans(implicit lang: Lang): String = PerfType.trans(this)

}

// ids: 0, 1, 2, 3, 5, 6 - used previously
object PerfType {

  case object RealTime
      extends PerfType(
        7,
        key = "realTime",
        icon = Icons.standard,
      )

  case object Correspondence
      extends PerfType(
        4,
        key = "correspondence",
        icon = Icons.correspondence,
      )

  case object Minishogi
      extends PerfType(
        12,
        key = "minishogi",
        icon = Icons.minishogi,
      )

  case object Chushogi
      extends PerfType(
        13,
        key = "chushogi",
        icon = Icons.chushogi,
      )

  case object Annanshogi
      extends PerfType(
        14,
        key = "annanshogi",
        icon = Icons.annanshogi,
      )

  case object Kyotoshogi
      extends PerfType(
        15,
        key = "kyotoshogi",
        icon = Icons.kyotoshogi,
      )

  case object Checkshogi
      extends PerfType(
        16,
        key = "checkshogi",
        icon = Icons.checkshogi,
      )

  case object Dobutsu
      extends PerfType(
        17,
        key = "dobutsu",
        icon = Icons.dobutsu,
      )

  case object Puzzle
      extends PerfType(
        20,
        key = "puzzle",
        icon = Icons.puzzle,
      )

  val all: List[PerfType] = List(
    RealTime,
    Correspondence,
    Minishogi,
    Chushogi,
    Annanshogi,
    Kyotoshogi,
    Checkshogi,
    Dobutsu,
    Puzzle,
  )
  private val byKeyMap = all map { p =>
    (p.key, p)
  } toMap
  private val byIdMap = all map { p =>
    (p.id, p)
  } toMap

  def byKey(key: Perf.Key): Option[PerfType] = byKeyMap get key
  def byId(id: Perf.ID): Option[PerfType]    = byIdMap get id

  def from(variant: shogi.variant.Variant, hasClock: Boolean): PerfType =
    byVariant(variant) getOrElse {
      if (hasClock) RealTime
      else Correspondence
    }

  val nonPuzzle: List[PerfType] = List(
    RealTime,
    Correspondence,
    Minishogi,
    Chushogi,
    Annanshogi,
    Kyotoshogi,
    Checkshogi,
    Dobutsu,
  )
  val leaderboardable: List[PerfType] = List(
    RealTime,
    Correspondence,
    Minishogi,
    Chushogi,
    Annanshogi,
    Kyotoshogi,
    Checkshogi,
    Dobutsu,
  )
  val variants: List[PerfType] = List(Minishogi, Chushogi, Annanshogi, Kyotoshogi, Checkshogi)
  val standard: List[PerfType] = List(RealTime, Correspondence)

  def variantOf(pt: PerfType): shogi.variant.Variant =
    pt match {
      case Kyotoshogi => shogi.variant.Kyotoshogi
      case Annanshogi => shogi.variant.Annanshogi
      case Checkshogi => shogi.variant.Checkshogi
      case Dobutsu    => shogi.variant.Dobutsu
      case Chushogi   => shogi.variant.Chushogi
      case Minishogi  => shogi.variant.Minishogi
      case _          => shogi.variant.Standard
    }

  def byVariant(variant: shogi.variant.Variant): Option[PerfType] =
    variant match {
      case shogi.variant.Kyotoshogi => Kyotoshogi.some
      case shogi.variant.Annanshogi => Annanshogi.some
      case shogi.variant.Dobutsu    => Dobutsu.some
      case shogi.variant.Checkshogi => Checkshogi.some
      case shogi.variant.Chushogi   => Chushogi.some
      case shogi.variant.Minishogi  => Minishogi.some
      case _                        => none
    }

  def trans(pt: PerfType)(implicit lang: Lang): String =
    pt match {
      case RealTime       => I18nKeys.shogi.txt()
      case Correspondence => I18nKeys.correspondence.txt()
      case Puzzle         => I18nKeys.puzzles.txt()
      case Minishogi      => I18nKeys.minishogi.txt()
      case Chushogi       => I18nKeys.chushogi.txt()
      case Annanshogi     => I18nKeys.annanshogi.txt()
      case Kyotoshogi     => I18nKeys.kyotoshogi.txt()
      case Checkshogi     => I18nKeys.checkshogi.txt()
      case Dobutsu        => I18nKeys.dobutsu.txt()
    }

}
