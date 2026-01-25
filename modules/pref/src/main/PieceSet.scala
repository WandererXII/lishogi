package lila.pref

// key is tied to file names and db, title can be easily changed
// pro piece sets don't in any way hint to how pieces move
class PieceSet private[pref] (val key: String, val name: String, val pro: Boolean = false) {

  override def toString = key

  def cssClass = key

}

sealed trait PieceSetBase {

  val all: List[PieceSet]

  val default: PieceSet

  lazy val allByKey = all map { p =>
    p.key -> p
  } toMap

  def apply(key: String) = allByKey.getOrElse(key.toLowerCase, PieceSetBase.default)

  def contains(key: String) = allByKey contains key.toLowerCase

}

object PieceSetBase {
  val default = new PieceSet("ryoko_1kanji", "Ryoko 1-Kanji", pro = true)
}

object PieceSet extends PieceSetBase {

  val default = PieceSetBase.default

  val all = List(
    default,
    new PieceSet("kanji_light", "Kanji Light", pro = true),
    new PieceSet("kanji_brown", "Kanji Brown", pro = true),
    new PieceSet("orangain", "orangain", pro = true),
    new PieceSet("kanji_red_wood", "Kanji Red Wood", pro = true),
    new PieceSet("portella", "Portella", pro = true),
    new PieceSet("portella_2kanji", "Portella 2-Kanji", pro = true),
    new PieceSet("1kanji_3d", "Kanji 3D", pro = true),
    new PieceSet("2kanji_3d", "2-Kanji 3D", pro = true),
    new PieceSet("hitomoji", "Hitomoji", pro = true),
    new PieceSet("shogi_cz", "Shogi.cz"),
    new PieceSet("shogi_fcz", "Czech"),
    new PieceSet("engraved_cz", "Engraved Shogi.cz"),
    new PieceSet("engraved_cz_bnw", "Engraved Shogi.cz - black and white"),
    new PieceSet("kanji_guide_shadowed", "Kanji Guide Shadowed"),
    new PieceSet("valdivia", "Valdivia"),
    new PieceSet("vald_opt", "Valdivia 2"),
    new PieceSet("shogi_bnw", "Shogi - black and white"),
    new PieceSet("simple_kanji", "Simple Kanji", pro = true),
    new PieceSet("pixel", "Pixel 8bit", pro = true),
    new PieceSet("better_8_bit", "8bit Pixel Alt", pro = true),
    new PieceSet("intl_colored_2d", "International Colored 2D"),
    new PieceSet("intl_colored_3d", "International Colored 3D"),
    new PieceSet("intl_shadowed", "International Shadowed"),
    new PieceSet("intl_monochrome_2d", "International Monochrome 2D"),
    new PieceSet("intl_wooden_3d", "International Wooden 3D"),
    new PieceSet("intl_portella", "International Portella"),
    new PieceSet("international", "International"),
    new PieceSet("firi", "Firi"),
    new PieceSet("joyful", "Joyful"),
    new PieceSet("characters", "Characters"),
    new PieceSet("dobutsu", "Dobutsu"),
    new PieceSet("logy_games", "Logy Games"),
    new PieceSet("western", "Western"),
    new PieceSet("blank", "Blank", pro = true),
    new PieceSet("invisible", "Invisible", pro = true),
  )

}

object ChuPieceSet extends PieceSetBase {

  val default = PieceSetBase.default

  val all = List(
    default,
    new PieceSet("eigetsu_gyoryu", "Eigetsu Gyoryu", pro = true),
    new PieceSet("intl", "International"),
    new PieceSet("fcz", "Czech"),
    new PieceSet("better_8_bit", "8bit Pixel Alt"),
    new PieceSet("intl_bnw", "International - black and white"),
    new PieceSet("firi", "Firi"),
    new PieceSet("mnemonic", "Mnemonic"),
    new PieceSet("blank", "Blank", pro = true),
    new PieceSet("invisible", "Invisible", pro = true),
  )

}

object KyoPieceSet extends PieceSetBase {

  val default = PieceSetBase.default

  val all = List(
    default,
    new PieceSet("orangain", "orangain", pro = true),
    new PieceSet("kanji", "Kanji with promotions", pro = true),
    new PieceSet("simple_kanji", "Simple Kanji", pro = true),
    new PieceSet("better_8_bit", "8bit Pixel Alt"),
    new PieceSet("intl", "International with promotions"),
    new PieceSet("international", "International"),
    new PieceSet("dobutsu", "Dobutsu"),
    new PieceSet("joyful", "Joyful"),
    new PieceSet("logy_games", "Logy Games"),
    new PieceSet("blank", "Blank", pro = true),
    new PieceSet("invisible", "Invisible", pro = true),
  )

}

object DobutsuPieceSet extends PieceSetBase {

  val default = new PieceSet("dobutsu", "Dobutsu", pro = true)

  val all = List(
    default,
  )

}
