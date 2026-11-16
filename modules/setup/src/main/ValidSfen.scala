package lila.setup

import shogi.format.forsyth.Sfen

case class ValidSfen(
    sfen: Sfen,
    position: shogi.Position,
) {

  def color = position.color
}

object ValidSfen {
  def apply(strict: Boolean, variant: shogi.variant.Variant)(sfen: Sfen): Option[ValidSfen] =
    for {
      parsed <- sfen.toPosition(variant)
      if parsed.playable(strict = strict)
    } yield ValidSfen(parsed.toSfen, parsed)
}
