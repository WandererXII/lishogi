package lila.study

import shogi.format.usi.Usi
import shogi.format.usi.UsiCharPair

import lila.game.Game

object GameToRoot {

  def apply(
      game: Game,
      withClocks: Boolean,
  ): Node.Root = {
    val clocks = withClocks ?? (
      for {
        initTime <- game.clock.map(_.config.initTime)
        times    <- game.bothClockStates
      } yield (initTime +: times)
    )

    apply(
      Node.GameMainline(
        id = game.id,
        part = 0,
        variant = game.variant,
        usis = game.usis,
        initialSfen = game.initialSfen,
        clocks = clocks,
      ),
    )
  }

  def apply(
      gm: Node.GameMainline,
  ): Node.Root = {
    val usis = gm.usis.take(Node.MAX_PLIES)
    shogi.Replay.positionsWhileValid(usis, gm.initialSfen, gm.variant) match {
      case (positionsWithInit, error) =>
        error foreach logShogiError(gm.id)
        // an unreadable initial sfen yields no positions, fall back to the variant's default
        val (init, positions) = positionsWithInit match {
          case head :: tail => (head, tail)
          case Nil          => (shogi.Position(gm.variant), Nil)
        }
        val root = Node.Root(
          ply = init.plies,
          sfen = init.toSfen,
          check = init.check,
          clock = gm.clocks.flatMap(_.headOption),
          gameMainline = gm.some,
          children = Node.emptyChildren,
        )
        def makeNode(p: shogi.Position, usi: Usi) =
          Node(
            id = UsiCharPair(usi, p.variant),
            ply = p.plies,
            usi = usi,
            sfen = p.toSfen,
            check = p.check,
            clock = gm.clocks flatMap (_ lift (p.plies - init.plies)),
            forceVariation = false,
            children = Node.emptyChildren,
          )

        positions.zip(usis).reverse match {
          case Nil => root
          case (p, m) :: rest =>
            root addChild rest.foldLeft(makeNode(p, m)) { case (node, (p, m)) =>
              makeNode(p, m) addChild node
            }
        }
    }
  }

  private val logShogiError = (id: String) =>
    (err: String) =>
      logger.warn(
        s"study.GameToRoot https://lishogi.org/$id ${err.linesIterator.toList.headOption}",
      )

}
