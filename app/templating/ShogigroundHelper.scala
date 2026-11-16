package lila.app
package templating

import shogi.Color
import shogi.Hand
import shogi.Square
import shogi.variant.Variant

import lila.app.ui.ScalatagsTemplate._
import lila.game.Pov

trait ShogigroundHelper {

  def shogiground(
      position: shogi.Position,
      orient: Color,
  ): Frag =
    sgWrap(position.variant, orient) {
      frag(
        shogigroundHand(
          Top,
          position.variant,
          !orient,
          position.hands(!orient),
        ),
        sgBoard(
          sgSquares,
          sgPieces {
            raw {
              val scale = 50
              def x(sq: Square) =
                orient.fold(
                  position.variant.numberOfFiles - sq.file.index - 1,
                  sq.file.index,
                ) * scale
              def y(sq: Square) =
                orient.fold(
                  sq.rank.index,
                  position.variant.numberOfRanks - sq.rank.index - 1,
                ) * scale
              position.board.pieces.map { case (sq, piece) =>
                val klass = s"${piece.color.name} ${piece.role.name}"
                s"""<piece class="$klass" style="transform: translate(${x(sq)}%, ${y(
                    sq,
                  )}%) scale(0.5)"></piece>"""
              } mkString ""
            }
          },
        ),
        shogigroundHand(
          Bottom,
          position.variant,
          orient,
          position.hands(orient),
        ),
      )
    }

  def shogiground(pov: Pov): Frag =
    shogiground(
      position = pov.game.position,
      orient = pov.color,
    )

  def shogigroundEmpty(variant: Variant, orient: Color) =
    sgWrap(variant, orient)(
      frag(
        shogigroundHand(Top, variant, !orient, Hand.empty),
        sgBoard(sgSquares),
        shogigroundHand(Bottom, variant, orient, Hand.empty),
      ),
    )

  private val sgBoard   = tag("sg-board")
  private val sgSquares = tag("sg-squares")
  private val sgPieces  = tag("sg-pieces")

  private val sgHandWrap = tag("sg-hand-wrap")
  private val sgHand     = tag("sg-hand")

  private def sgWrap(variant: Variant, orient: Color)(content: Frag): Frag =
    div(
      cls := s"sg-wrap d-${variant.numberOfFiles}x${variant.numberOfRanks} orientation-${orient.name} preload",
    )(
      content,
    )

  sealed private trait Position
  private case object Top    extends Position
  private case object Bottom extends Position

  private def shogigroundHand(
      position: Position,
      variant: Variant,
      color: Color,
      hand: Hand,
  ): Option[Frag] =
    variant.supportsDrops option sgHandWrap(
      (cls := s"hand-${position.toString.toLowerCase} r-${variant.handRoles.size}"),
    ) {
      sgHand {
        raw {
          variant.handRoles.map { role =>
            s"""<sg-hp-wrap data-nb="${hand(
                role,
              )}"><piece class="${color.name} ${role.name}"></piece></sg-hp-wrap>"""
          } mkString ""
        }
      }
    }

}
