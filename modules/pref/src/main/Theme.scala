package lila.pref

sealed class Theme private[pref] (val name: String, val colors: Theme.HexColors) {

  override def toString = name

  def cssClass = name

  def light = colors._1
  def dark  = colors._2
}

sealed trait ThemeObject {

  val all: List[Theme]

  val default: Theme

  lazy val allByName = all map { c =>
    c.name -> c
  } toMap

  def apply(name: String) = allByName.getOrElse(name, default)

  def contains(name: String) = allByName contains name
}

object Theme extends ThemeObject {

  case class HexColor(value: String) extends AnyVal with StringValue
  type HexColors = (HexColor, HexColor)

  private[pref] val defaultHexColors = (HexColor("b0b0b0"), HexColor("909090"))

  private val colors: Map[String, HexColors] = Map(
    "blue"  -> (HexColor("dee3e6") -> HexColor("8ca2ad")),
    "brown" -> (HexColor("f0d9b5") -> HexColor("b58863"))
  )

  val all = List(
    "solid-orange",
    "solid-natural",
    "wood1",
    "kaya1",
    "kaya2",
    "kaya-light",
    "oak",
    "solid-brown1",
    "solid-wood1",
    "blue",
    "dark-blue",
    "gray",
    "Painting1",
    "Painting2",
    "Kinkaku",
    "space1",
    "space2",
    "whiteBoard",
    "darkBoard",
    "doubutsu",
    "transparent",
    "transparent-white"
  ) map { name =>
    new Theme(name, colors.getOrElse(name, defaultHexColors))
  }

  lazy val default = allByName get "wood1" err "Can't find default theme D:"
}

object ThemeTall extends ThemeObject {

  val all = List(
    "t_solid-orange",
    "t_solid-natural",
    "t_wood1",
    "t_kaya1",
    "t_kaya2",
    "t_kaya-light",
    "t_oak",
    "t_solid-brown1",
    "t_solid-wood1",
    "t_Painting1",
    "t_Painting2",
    "t_Kinkaku",
    "t_space1",
    "t_space2",
    "t_whiteBoard",
    "t_darkBoard",
    "t_doubutsu",
    "t_transparent",
    "t_transparent-white"
  ) map { name =>
    new Theme(name, Theme.defaultHexColors)
  }

  lazy val default = allByName get "t_wood1" err "Can't find default theme D:"
}
