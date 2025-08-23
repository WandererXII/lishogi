package lila.rating

import play.api.i18n.Lang

import shogi.Centis
import shogi.Speed

import lila.common.Icons
import lila.i18n.I18nKeys

sealed abstract class PerfType(
    val id: Perf.ID,
    val key: Perf.Key,
    private val name: String,
    private val title: String,
    val icon: String,
) {

  def trans(implicit lang: Lang): String = PerfType.trans(this)

  def desc(implicit lang: Lang): String = PerfType.desc(this)
}

object PerfType {

  case object UltraBullet
      extends PerfType(
        0,
        key = "ultraBullet",
        name = Speed.UltraBullet.name,
        title = Speed.UltraBullet.title,
        icon = Icons.ultraBullet,
      )

  case object Bullet
      extends PerfType(
        1,
        key = "bullet",
        name = Speed.Bullet.name,
        title = Speed.Bullet.title,
        icon = Icons.bullet,
      )

  case object Blitz
      extends PerfType(
        2,
        key = "blitz",
        name = Speed.Blitz.name,
        title = Speed.Blitz.title,
        icon = Icons.blitz,
      )

  case object Rapid
      extends PerfType(
        6,
        key = "rapid",
        name = Speed.Rapid.name,
        title = Speed.Rapid.title,
        icon = Icons.rapid,
      )

  case object Classical
      extends PerfType(
        3,
        key = "classical",
        name = Speed.Classical.name,
        title = Speed.Classical.title,
        icon = Icons.classical,
      )

  case object Correspondence
      extends PerfType(
        4,
        key = "correspondence",
        name = "Correspondence",
        title = Speed.Correspondence.title,
        icon = Icons.correspondence,
      )

  case object Standard
      extends PerfType(
        5,
        key = "standard",
        name = shogi.variant.Standard.name,
        title = "Standard rules of shogi",
        icon = Icons.standard,
      )

  case object Minishogi
      extends PerfType(
        12,
        key = "minishogi",
        name = shogi.variant.Minishogi.name,
        title = "Standard rules of shogi, but smaller board",
        icon = Icons.minishogi,
      )

  case object Chushogi
      extends PerfType(
        13,
        key = "chushogi",
        name = shogi.variant.Chushogi.name,
        title = "Most popular large board shogi variant",
        icon = Icons.chushogi,
      )

  case object Annanshogi
      extends PerfType(
        14,
        key = "annanshogi",
        name = shogi.variant.Annanshogi.name,
        title = shogi.variant.Annanshogi.title,
        icon = Icons.annanshogi,
      )

  case object Kyotoshogi
      extends PerfType(
        15,
        key = "kyotoshogi",
        name = shogi.variant.Kyotoshogi.name,
        title = shogi.variant.Kyotoshogi.title,
        icon = Icons.kyotoshogi,
      )

  case object Checkshogi
      extends PerfType(
        16,
        key = "checkshogi",
        name = shogi.variant.Checkshogi.name,
        title = shogi.variant.Checkshogi.title,
        icon = Icons.checkshogi,
      )

  case object Puzzle
      extends PerfType(
        20,
        key = "puzzle",
        name = "Training",
        title = "Shogi tactics trainer",
        icon = Icons.puzzle,
      )

  val all: List[PerfType] = List(
    UltraBullet,
    Bullet,
    Blitz,
    Rapid,
    Classical,
    Correspondence,
    Standard,
    Minishogi,
    Chushogi,
    Annanshogi,
    Kyotoshogi,
    Checkshogi,
    Puzzle,
  )
  val byKey = all map { p =>
    (p.key, p)
  } toMap
  val byId = all map { p =>
    (p.id, p)
  } toMap

  val default = Standard

  def apply(key: Perf.Key): Option[PerfType] = byKey get key
  def orDefault(key: Perf.Key): PerfType     = apply(key) | default

  def apply(id: Perf.ID): Option[PerfType] = byId get id

  // def name(key: Perf.Key): Option[String] = apply(key) map (_.name)

  def id2key(id: Perf.ID): Option[Perf.Key] = byId get id map (_.key)

  val nonPuzzle: List[PerfType] = List(
    UltraBullet,
    Bullet,
    Blitz,
    Rapid,
    Classical,
    Correspondence,
    Minishogi,
    Chushogi,
    Annanshogi,
    Kyotoshogi,
    Checkshogi,
  )
  val leaderboardable: List[PerfType] = List(
    Bullet,
    Blitz,
    Rapid,
    Classical,
    UltraBullet,
    Correspondence,
    Minishogi,
    Chushogi,
    Annanshogi,
    Kyotoshogi,
    Checkshogi,
  )
  val variants: List[PerfType] = List(Minishogi, Chushogi, Annanshogi, Kyotoshogi)
  val standard: List[PerfType] = List(Bullet, Blitz, Rapid, Classical, Correspondence)

  def variantOf(pt: PerfType): shogi.variant.Variant =
    pt match {
      case Kyotoshogi => shogi.variant.Kyotoshogi
      case Annanshogi => shogi.variant.Annanshogi
      case Checkshogi => shogi.variant.Checkshogi
      case Chushogi   => shogi.variant.Chushogi
      case Minishogi  => shogi.variant.Minishogi
      case _          => shogi.variant.Standard
    }

  def byVariant(variant: shogi.variant.Variant): Option[PerfType] =
    variant match {
      case shogi.variant.Kyotoshogi => Kyotoshogi.some
      case shogi.variant.Annanshogi => Annanshogi.some
      case shogi.variant.Checkshogi => Checkshogi.some
      case shogi.variant.Chushogi   => Chushogi.some
      case shogi.variant.Minishogi  => Minishogi.some
      case _                        => none
    }

  def standardBySpeed(speed: Speed): PerfType = speed match {
    case Speed.UltraBullet    => UltraBullet
    case Speed.Bullet         => Bullet
    case Speed.Blitz          => Blitz
    case Speed.Rapid          => Rapid
    case Speed.Classical      => Classical
    case Speed.Correspondence => Correspondence
  }

  def apply(variant: shogi.variant.Variant, speed: Speed): PerfType =
    byVariant(variant) getOrElse standardBySpeed(speed)

  lazy val totalTimeRoughEstimation: Map[PerfType, Centis] = nonPuzzle.view
    .map { pt =>
      pt -> Centis(pt match {
        case UltraBullet    => 25 * 100
        case Bullet         => 90 * 100
        case Blitz          => 7 * 60 * 100
        case Rapid          => 12 * 60 * 100
        case Classical      => 30 * 60 * 100
        case Correspondence => 60 * 60 * 100
        case Chushogi       => 30 * 60 * 100
        case _              => 7 * 60 * 100
      })
    }
    .to(Map)

  def iconByVariant(variant: shogi.variant.Variant): String =
    byVariant(variant).fold(Icons.standard)(_.icon)

  def trans(pt: PerfType)(implicit lang: Lang): String =
    pt match {
      case UltraBullet    => I18nKeys.ultrabullet.txt()
      case Bullet         => I18nKeys.bullet.txt()
      case Blitz          => I18nKeys.blitz.txt()
      case Rapid          => I18nKeys.rapid.txt()
      case Classical      => I18nKeys.classical.txt()
      case Correspondence => I18nKeys.correspondence.txt()
      case Puzzle         => I18nKeys.puzzles.txt()
      case Minishogi      => I18nKeys.minishogi.txt()
      case Chushogi       => I18nKeys.chushogi.txt()
      case Annanshogi     => I18nKeys.annanshogi.txt()
      case Kyotoshogi     => I18nKeys.kyotoshogi.txt()
      case Checkshogi     => I18nKeys.checkshogi.txt()
      case pt             => pt.name
    }

  val translated: Set[PerfType] =
    Set(
      UltraBullet,
      Bullet,
      Blitz,
      Rapid,
      Classical,
      Correspondence,
      Puzzle,
      Minishogi,
      Chushogi,
      Annanshogi,
      Kyotoshogi,
      Checkshogi,
    )

  def desc(pt: PerfType)(implicit lang: Lang): String =
    pt match {
      case UltraBullet    => I18nKeys.ultraBulletDesc.txt()
      case Bullet         => I18nKeys.bulletDesc.txt()
      case Blitz          => I18nKeys.blitzDesc.txt()
      case Rapid          => I18nKeys.rapidDesc.txt()
      case Classical      => I18nKeys.classicalDesc.txt()
      case Correspondence => I18nKeys.correspondenceDesc.txt()
      case Puzzle         => I18nKeys.puzzleDesc.txt()
      case pt             => pt.title
    }
}
