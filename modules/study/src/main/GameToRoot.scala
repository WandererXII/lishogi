package lishogi.study

import shogi.format.FEN
import lishogi.game.Game
import lishogi.round.JsonView.WithFlags

private object GameToRoot {

  def apply(game: Game, initialFen: Option[FEN], withClocks: Boolean): Node.Root = {
    val root = Node.Root.fromRoot {
      lishogi.round.TreeBuilder(
        game = game,
        analysis = none,
        initialFen = initialFen | FEN(game.variant.initialFen),
        withFlags = WithFlags(clocks = withClocks)
      )
    }
    endComment(game).fold(root) { comment =>
      root updateMainlineLast { _.setComment(comment) }
    }
  }

  private def endComment(game: Game) =
    game.finished option {
      import lishogi.tree.Node.Comment
      val status = lishogi.game.StatusText(game)
      val text   = s"$status"
      Comment(Comment.Id.make, Comment.Text(text), Comment.Author.Lishogi)
    }
}
