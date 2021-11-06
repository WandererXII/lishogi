package lishogi.study

import scala.util.chaining._

import shogi.format.FEN
import shogi.format.kif.KifParser
import shogi.format.csa.CsaParser
import lishogi.game.{ Game, Namer }
import lishogi.tree.Node.Comment

final private class ExplorerGame(
    importer: lishogi.explorer.ExplorerImporter,
    lightUserApi: lishogi.user.LightUserApi,
    net: lishogi.common.config.NetConfig
)(implicit ec: scala.concurrent.ExecutionContext) {

  def quote(gameId: Game.ID): Fu[Option[Comment]] =
    importer(gameId) map {
      _ ?? { game =>
        gameComment(game).some
      }
    }

  def insert(study: Study, position: Position, gameId: Game.ID): Fu[Option[(Chapter, Path)]] =
    if (position.chapter.isOverweight) {
      logger.info(s"Overweight chapter ${study.id}/${position.chapter.id}")
      fuccess(none)
    } else
      importer(gameId) map {
        _ ?? { game =>
          position.node ?? { fromNode =>
            GameToRoot(game, none, withClocks = false).pipe { root =>
              root.setCommentAt(
                comment = gameComment(game),
                path = Path(root.mainline.map(_.id))
              )
            } ?? { gameRoot =>
              merge(fromNode, position.path, gameRoot) flatMap { case (newNode, path) =>
                position.chapter.addNode(newNode, path) map (_ -> path)
              }
            }
          }
        }
      }

  private def merge(fromNode: RootOrNode, fromPath: Path, game: Node.Root): Option[(Node, Path)] = {
    val gameNodes = game.mainline.dropWhile(n =>
      !shogi.format.Forsyth.compareTruncated(n.fen.value, fromNode.fen.value)
    ) drop 1
    val (path, foundGameNode) = gameNodes.foldLeft((Path.root, none[Node])) {
      case ((path, None), gameNode) =>
        val nextPath = path + gameNode
        if (fromNode.children.nodeAt(nextPath).isDefined) (nextPath, none)
        else (path, gameNode.some)
      case (found, _) => found
    }
    foundGameNode.map { _ -> fromPath.+(path) }
  }

  private def gameComment(game: Game) =
    Comment(
      id = Comment.Id.make,
      text = Comment.Text(s"${gameTitle(game)}, ${gameUrl(game)}"),
      by = Comment.Author.Lishogi
    )

  private def gameUrl(game: Game) = s"${net.baseUrl}/${game.id}"

  private def gameTitle(g: Game): String = {
    val pgn = g.notationImport.flatMap(ni =>
      if (ni.isCsa) CsaParser.full(ni.notation).toOption else KifParser.full(ni.notation).toOption
    )
    val sente  = pgn.flatMap(_.tags(_.Sente)) | Namer.playerTextBlocking(g.sentePlayer)(lightUserApi.sync)
    val gote   = pgn.flatMap(_.tags(_.Gote)) | Namer.playerTextBlocking(g.gotePlayer)(lightUserApi.sync)
    val result = shogi.Color.showResult(g.winnerColor)
    val event: Option[String] =
      (pgn.flatMap(_.tags(_.Event)), pgn.flatMap(_.tags.year).map(_.toString)) match {
        case (Some(event), Some(year)) if event.contains(year) => event.some
        case (Some(event), Some(year))                         => s"$event, $year".some
        case (eventO, yearO)                                   => eventO orElse yearO
      }
    s"$sente - $gote, $result, ${event | "-"}"
  }
}
