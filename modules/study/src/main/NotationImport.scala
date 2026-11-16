package lila.study

import cats.data.Validated

import shogi.format.Glyphs
import shogi.format.ParsedNotation
import shogi.format.ParsedStep
import shogi.format.Tags
import shogi.format.usi.UsiCharPair

import lila.common.LightUser
import lila.game.Game
import lila.importer.ImportData
import lila.importer.Preprocessed
import lila.tree.Node.Comment
import lila.tree.Node.Comments
import lila.tree.Node.Shapes

object NotationImport {

  case class Result(
      root: Node.Root,
      variant: shogi.variant.Variant,
      tags: Tags,
      endStatus: Option[Chapter.EndStatus],
  )

  def apply(notation: String, contributors: List[LightUser]): Validated[String, Result] =
    ImportData(notation, analyse = none).preprocess(user = none).map {
      case Preprocessed(game, setup, parsedNotation) =>
        val annotator = findAnnotator(parsedNotation, contributors)
        parseComments(parsedNotation.initialPosition.comments, annotator) match {
          case (shapes, comments) =>
            val root = Node.Root(
              ply = setup.plies,
              sfen = game.initialSfen | game.variant.initialSfen,
              check = setup.check,
              shapes = shapes,
              comments = comments,
              glyphs = Glyphs.empty,
              clock = lila.game.clock.NotationClock.config(parsedNotation.tags).map(_.limit),
              gameMainline = none,
              children = Node.Children {
                val variations =
                  makeVariations(parsedNotation.parsedSteps.value, setup, annotator)
                makeNode(
                  prev = setup,
                  parsedSteps = parsedNotation.parsedSteps.value,
                  annotator = annotator,
                ).fold(variations)(_ :: variations).toVector
              },
            )
            val endStatus: Option[Chapter.EndStatus] = (game.finished option game.status).map {
              status =>
                Chapter.EndStatus(
                  status = status,
                  winner = game.winnerColor,
                )
            }
            Result(
              root = root,
              variant = game.variant,
              tags = StudyTags(parsedNotation.tags), // tags in studies are kif format even for CSA
              endStatus = endStatus,
            )
        }
    }

  def userAnalysis(notation: String): Validated[String, (Game, Node.Root, Tags)] =
    ImportData(notation, analyse = none).preprocess(user = none).map {
      case Preprocessed(game, setup, parsedNotation) =>
        val annotator = findAnnotator(parsedNotation, Nil)
        parseComments(parsedNotation.initialPosition.comments, annotator) match {
          case (shapes, comments) =>
            val root = Node.Root(
              ply = setup.plies,
              sfen = game.initialSfen | game.variant.initialSfen,
              check = setup.check,
              shapes = shapes,
              comments = comments,
              glyphs = Glyphs.empty,
              clock = lila.game.clock.NotationClock.config(parsedNotation.tags).map(_.limit),
              gameMainline = none,
              children = Node.Children {
                val variations =
                  makeVariations(parsedNotation.parsedSteps.value, setup, annotator)
                makeNode(
                  prev = setup,
                  parsedSteps = parsedNotation.parsedSteps.value,
                  annotator = annotator,
                ).fold(variations)(_ :: variations).toVector
              },
            )
            (game withId "synthetic", root, parsedNotation.tags)
        }
    }

  private def findAnnotator(
      notation: ParsedNotation,
      contributors: List[LightUser],
  ): Option[Comment.Author] =
    notation tags "annotator" map { a =>
      val lowered = a.toLowerCase
      contributors.find { c =>
        lowered.split(" ").contains(c.id) || lowered.endsWith(s"/${c.id}")
      } map { c =>
        Comment.Author.User(c.id, c.name)
      } getOrElse Comment.Author.External(a)
    }

  private def makeVariations(
      parsedSteps: List[ParsedStep],
      position: shogi.Position,
      annotator: Option[Comment.Author],
  ) =
    parsedSteps.headOption.?? {
      _.metas.variations.flatMap { variation =>
        makeNode(position, variation.value, annotator)
      }
    }

  private def parseComments(
      comments: List[String],
      annotator: Option[Comment.Author],
  ): (Shapes, Comments) =
    comments.reverse.foldLeft((Shapes(Nil), Comments(Nil))) { case ((shapes, comments), txt) =>
      CommentParser(txt) match {
        case CommentParser.ParsedComment(s, str) =>
          (
            (shapes ++ s),
            (str.trim match {
              case "" => comments
              case com =>
                comments + Comment(
                  Comment.Id.make,
                  Comment.Text(com),
                  annotator | Comment.Author.Unknown,
                )
            }),
          )
      }
    }

  private def makeNode(
      prev: shogi.Position,
      parsedSteps: List[ParsedStep],
      annotator: Option[Comment.Author],
  ): Option[Node] =
    try {
      parsedSteps match {
        case Nil => none
        case parsedStep :: rest =>
          prev(parsedStep).fold(
            _ => none, // illegal step; stop here.
            game =>
              game.history.lastUsi flatMap { usi =>
                parseComments(parsedStep.metas.comments, annotator) match {
                  case (shapes, comments) =>
                    Node(
                      id = UsiCharPair(usi, game.variant),
                      ply = game.plies,
                      usi = usi,
                      sfen = game.toSfen,
                      check = game.check,
                      shapes = shapes,
                      comments = comments,
                      glyphs = parsedStep.metas.glyphs,
                      // Normally we store time remaining after turn,
                      // which is pretty useless with byo...
                      // for imports we are gonna store time spent on this step
                      clock = parsedStep.metas.timeSpent.map(d =>
                        lila.common.Centis.ofMillis(d.toMillis),
                      ),
                      children = removeDuplicatedChildrenFirstNode {
                        val variations = makeVariations(rest, game, annotator)
                        Node.Children {
                          makeNode(game, rest, annotator).fold(variations)(_ :: variations).toVector
                        }
                      },
                      forceVariation = false,
                    ).some
                }
              },
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
