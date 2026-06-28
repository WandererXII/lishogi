package lila.article

object Gradient {

  // for good color randomization
  private def hash(seed: Int): Int = {
    var x = seed
    x ^= x << 13
    x ^= x >>> 17
    x ^= x << 5
    x & 0x7fffffff
  }

  private def fromString(s: String): String = {
    val h1 = s.hashCode & 0x7fffffff
    val h2 = hash(h1)
    val h3 = hash(h2)

    val baseHue = h1 % 360

    val hue1 = baseHue
    val hue2 = (baseHue + 30 + (h2 % 30)) % 360
    val hue3 = (baseHue + 60 + (h3 % 30)) % 360

    val sat1 = 55 + (h1 % 15)
    val sat2 = 50 + (h2 % 15)
    val sat3 = 45 + (h3 % 15)

    val light1 = 35 + (h1 % 10)
    val light2 = 25 + (h2 % 10)
    val light3 = 15 + (h3 % 10)

    val angle = 120 + (h1 % 50)

    s"""
      background:
        linear-gradient(
          ${angle}deg,
          hsl($hue1, $sat1%, $light1%),
          hsl($hue2, $sat2%, $light2%),
          hsl($hue3, $sat3%, $light3%)
        );
    """.trim.replaceAll("\\s+", " ")
  }

  def div(seed: String): String = {
    s"""<div class="gradient" style="${fromString(seed)}"></div>"""
  }
}
