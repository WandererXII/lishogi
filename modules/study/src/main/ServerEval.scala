package lila.study

import scala.concurrent.duration._

import play.api.libs.json._

import shogi.format.Glyphs
import shogi.format.usi.UciToUsi
import shogi.format.usi.Usi
import shogi.format.usi.UsiCharPair

import lila.analyse.Analysis
import lila.analyse.Info
import lila.hub.actorApi.shoginet.PostGameStudyRequest
import lila.hub.actorApi.shoginet.StudyChapterRequest
import lila.tree.Node.Comment
import lila.user.User

object ServerEval {

  final class Requester(
      shoginet: lila.hub.actors.Shoginet,
      chapterRepo: ChapterRepo,
  )(implicit ec: scala.concurrent.ExecutionContext) {

    private val onceEvery = lila.memo.OnceEvery(5 minutes)

    def apply(study: Study, chapter: Chapter, userId: User.ID): Funit =
      chapter.serverEval.fold(true) { eval =>
        !eval.done && onceEvery(chapter.id.value)
      } ?? {
        chapterRepo.startServerEval(chapter) >>- {
          chapter.setup.gameId
            .ifTrue(chapter.isFirstGameRootChapter)
            .fold {
              shoginet ! StudyChapterRequest(
                studyId = study.id.value,
                chapterId = chapter.id.value,
                initialSfen = chapter.root.sfen.some,
                variant = chapter.setup.variant,
                moves = chapter.root.mainline.map(_.usi).toList,
                userId = userId,
              )
            } { gameId =>
              shoginet ! PostGameStudyRequest(
                userId = userId,
                gameId = gameId,
                studyId = study.id.value,
                chapterId = chapter.id.value,
              )
            }
        }
      }
  }

  final class Merger(
      sequencer: StudySequencer,
      socket: StudySocket,
      chapterRepo: ChapterRepo,
  )(implicit ec: scala.concurrent.ExecutionContext) {

    def apply(analysis: Analysis, complete: Boolean): Funit =
      analysis.studyId.map(Study.Id.apply) ?? { studyId =>
        sequencer.sequenceStudyWithChapter(studyId, Chapter.Id(analysis.id)) {
          case Study.WithChapter(_, chapter) =>
            (complete ?? chapterRepo.completeServerEval(chapter)) >> {
              lila.common.Future
                .fold(chapter.root.mainline.zip(analysis.infoAdvices).toList)(Path.root) {
                  case (path, (node, (info, advOpt))) =>
                    chapter.root.nodeAt(path).flatMap { parent =>
                      analysisLine(parent, chapter.setup.variant, info) map { subTree =>
                        parent.addChild(subTree) -> subTree
                      }
                    } ?? { case (newParent, subTree) =>
                      chapterRepo.addSubTree(subTree, newParent, path)(chapter)
                    } >> {
                      import BSONHandlers._
                      import Node.{ BsonFields => F }
                      ((info.eval.score.isDefined && node.score.isEmpty) || (advOpt.isDefined && !node.comments.hasLishogiComment)) ??
                        chapterRepo
                          .setNodeValues(
                            chapter,
                            path + node,
                            List(
                              F.score -> info.eval.score
                                .ifTrue {
                                  node.score.isEmpty ||
                                  advOpt.isDefined && node.comments
                                    .findBy(Comment.Author.Lishogi)
                                    .isEmpty
                                }
                                .flatMap(EvalScoreBSONHandler.writeOpt),
                              F.comments -> advOpt
                                .map { adv =>
                                  node.comments + Comment(
                                    Comment.Id.make,
                                    Comment.Text(
                                      adv.makeComment(withEval = false, withBestMove = true),
                                    ),
                                    Comment.Author.Lishogi,
                                  )
                                }
                                .flatMap(CommentsBSONHandler.writeOpt),
                              F.glyphs -> advOpt
                                .map { adv =>
                                  node.glyphs merge Glyphs.fromList(List(adv.judgment.glyph))
                                }
                                .flatMap(GlyphsBSONHandler.writeOpt),
                            ),
                          )
                    } inject path + node
                } void
            } >>- {
              chapterRepo.byId(Chapter.Id(analysis.id)).foreach {
                _ ?? { chapter =>
                  socket.onServerEval(
                    studyId,
                    ServerEval.Progress(
                      chapterId = chapter.id,
                      root = chapter.root,
                      analysis = toJson(chapter, analysis),
                    ),
                  )
                }
              }
            } logFailure logger
        }
      }

    def postGameStudies(analysis: Analysis, complete: Boolean) =
      analysis.postGameStudies foreach { pgs =>
        apply(analysis.copy(id = pgs.chapterId, studyId = pgs.studyId.some), complete)
      }

    private def analysisLine(
        root: RootOrNode,
        variant: shogi.variant.Variant,
        info: Info,
    ): Option[Node] = {
      val variation = info.variation take 15
      val usis      = ~Usi.readList(variation).orElse(UciToUsi.readList(variation))
      shogi.Replay.positionsWhileValid(usis, root.sfen.some, variant) match {
        case (positions, error) =>
          error foreach { logger.info(_) }
          positions.drop(1).zip(usis).reverse match {
            case Nil => none
            case (p, m) :: rest =>
              rest
                .foldLeft(makeBranch(p, m)) { case (node, (p, m)) =>
                  makeBranch(p, m) addChild node
                } some
          }
      }
    }

    private def makeBranch(p: shogi.Position, usi: Usi) =
      Node(
        id = UsiCharPair(usi, p.variant),
        ply = p.plies,
        usi = usi,
        sfen = p.toSfen,
        check = p.check,
        clock = none,
        children = Node.emptyChildren,
        forceVariation = false,
      )
  }

  case class Progress(
      chapterId: Chapter.Id,
      root: Node.Root,
      analysis: JsObject,
  )

  def toJson(chapter: Chapter, analysis: Analysis) =
    lila.analyse.JsonView.bothPlayers(
      lila.analyse.Accuracy.PovLike(shogi.Sente, chapter.root.color, chapter.root.ply),
      analysis,
    )
}
