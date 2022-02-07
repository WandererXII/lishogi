package lila.study

import cats.data.Validated

import shogi.format.{ Glyphs, ParsedMove, ParsedNotation, Tags }
import shogi.format.forsyth.Sfen
import shogi.format.usi.UsiCharPair

import lila.common.LightUser
import lila.importer.{ ImportData, Preprocessed }
import lila.tree.Node.{ Comment, Comments, Shapes }

import lila.game.Game

object NotationImport {

  case class Result(
      root: Node.Root,
      variant: shogi.variant.Variant,
      tags: Tags,
      end: Option[End]
  )

  case class End(
      status: shogi.Status,
      winner: Option[shogi.Color],
      resultText: String,
      statusText: String
  )

  def apply(notation: String, contributors: List[LightUser]): Validated[String, Result] =
    ImportData(notation, analyse = none).preprocess(user = none).map {
      case Preprocessed(game, replay, parsedNotation) =>
        val annotator = findAnnotator(parsedNotation, contributors)
        parseComments(parsedNotation.initialPosition.comments, annotator) match {
          case (shapes, comments) =>
            val root = Node.Root(
              ply = replay.setup.plies,
              sfen = game.initialSfen | game.variant.initialSfen,
              check = replay.setup.situation.check,
              shapes = shapes,
              comments = comments,
              glyphs = Glyphs.empty,
              clock = parsedNotation.tags.clockConfig.map(_.limit),
              children = Node.Children {
                val variations = makeVariations(parsedNotation.parsedMoves.value, replay.setup, annotator)
                makeNode(
                  prev = replay.setup,
                  parsedMoves = parsedNotation.parsedMoves.value,
                  annotator = annotator
                ).fold(variations)(_ :: variations).toVector
              }
            )
            val end: Option[End] = (game.finished option game.status).map { status =>
              End(
                status = status,
                winner = game.winnerColor,
                resultText = shogi.Color.showResult(game.winnerColor),
                statusText = lila.game.StatusText(status, game.winnerColor, game.variant)
              )
            }
            val commented =
              if (root.mainline.lastOption.??(_.isCommented)) root
              else
                end.map(endComment).fold(root) { comment =>
                  root updateMainlineLast { _.setComment(comment) }
                }
            Result(
              root = commented,
              variant = game.variant,
              tags = KifTags(parsedNotation.tags), // tags in studies are kif format even for CSA
              end = end
            )
        }
    }

  def userAnalysis(notation: String): Validated[String, (Game, Node.Root, Tags)] =
    ImportData(notation, analyse = none).preprocess(user = none).map {
      case Preprocessed(game, replay, parsedNotation) =>
        val annotator = findAnnotator(parsedNotation, Nil)
        parseComments(parsedNotation.initialPosition.comments, annotator) match {
          case (shapes, comments) =>
            val root = Node.Root(
              ply = replay.setup.plies,
              sfen = game.initialSfen | game.variant.initialSfen,
              check = replay.setup.situation.check,
              shapes = shapes,
              comments = comments,
              glyphs = Glyphs.empty,
              clock = parsedNotation.tags.clockConfig.map(_.limit),
              children = Node.Children {
                val variations = makeVariations(parsedNotation.parsedMoves.value, replay.setup, annotator)
                makeNode(
                  prev = replay.setup,
                  parsedMoves = parsedNotation.parsedMoves.value,
                  annotator = annotator
                ).fold(variations)(_ :: variations).toVector
              }
            )
            (game withId "synthetic", root, parsedNotation.tags)
        }
    }

  private def findAnnotator(notation: ParsedNotation, contributors: List[LightUser]): Option[Comment.Author] =
    notation tags "annotator" map { a =>
      val lowered = a.toLowerCase
      contributors.find { c =>
        c.name == lowered || c.titleName == lowered || lowered.endsWith(s"/${c.id}")
      } map { c =>
        Comment.Author.User(c.id, c.titleName)
      } getOrElse Comment.Author.External(a)
    }

  private def endComment(end: End): Comment = {
    import lila.tree.Node.Comment
    import end._
    val text = s"$statusText"
    Comment(Comment.Id.make, Comment.Text(text), Comment.Author.Lishogi)
  }

  private def makeVariations(
      parsedMoves: List[ParsedMove],
      game: shogi.Game,
      annotator: Option[Comment.Author]
  ) =
    parsedMoves.headOption.?? {
      _.metas.variations.flatMap { variation =>
        makeNode(game, variation.value, annotator)
      }
    }

  private def parseComments(
      comments: List[String],
      annotator: Option[Comment.Author]
  ): (Shapes, Comments) =
    comments.reverse.foldLeft((Shapes(Nil), Comments(Nil))) { case ((shapes, comments), txt) =>
      CommentParser(txt) match {
        case CommentParser.ParsedComment(s, str) =>
          (
            (shapes ++ s),
            (str.trim match {
              case "" => comments
              case com =>
                comments + Comment(Comment.Id.make, Comment.Text(com), annotator | Comment.Author.Unknown)
            })
          )
      }
    }

  private def makeNode(
      prev: shogi.Game,
      parsedMoves: List[ParsedMove],
      annotator: Option[Comment.Author]
  ): Option[Node] =
    try {
      parsedMoves match {
        case Nil => none
        case parsedMove :: rest =>
          prev(parsedMove).fold(
            _ => none, // illegal move; stop here.
            game =>
              game.history.lastMove flatMap { usi =>
                parseComments(parsedMove.metas.comments, annotator) match {
                  case (shapes, comments) =>
                    Node(
                      id = UsiCharPair(usi, game.variant),
                      ply = game.plies,
                      usi = usi,
                      sfen = game.toSfen,
                      check = game.situation.check,
                      shapes = shapes,
                      comments = comments,
                      glyphs = parsedMove.metas.glyphs,
                      // Normally we store time remaining after turn,
                      // which is pretty useless with byo...
                      // for imports we are gonna store time spent on this move
                      clock = parsedMove.metas.timeSpent,
                      children = removeDuplicatedChildrenFirstNode {
                        val variations = makeVariations(rest, game, annotator)
                        Node.Children {
                          makeNode(game, rest, annotator).fold(variations)(_ :: variations).toVector
                        }
                      },
                      forceVariation = false
                    ).some
                }
              }
          )
      }
    } catch {
      case _: StackOverflowError =>
        logger.warn(s"study NotationImport.makeNode StackOverflowError")
        None
    }

  private def removeDuplicatedChildrenFirstNode(children: Node.Children): Node.Children =
    children.first match {
      case Some(main) if children.variations.exists(_.id == main.id) =>
        Node.Children {
          main +: children.variations.flatMap { node =>
            if (node.id == main.id) node.children.nodes
            else Vector(node)
          }
        }
      case _ => children
    }
}
