package lishogi.study

import akka.stream.scaladsl._
import shogi.format.kif.Kif
import shogi.format.csa.Csa
import shogi.format.{ Forsyth, Glyphs, Initial, NotationMove, Tag, Tags }
import org.joda.time.format.DateTimeFormat

import lishogi.common.String.slugify
import lishogi.tree.Node.{ Comment, Comments, Shape, Shapes }

final class NotationDump(
    chapterRepo: ChapterRepo,
    lightUserApi: lishogi.user.LightUserApi,
    net: lishogi.common.config.NetConfig
) {

  import NotationDump._

  def apply(study: Study, flags: WithFlags): Source[String, _] =
    chapterRepo
      .orderedByStudySource(study.id)
      .map(ofChapter(study, flags))
      .map(_.toString)
      .intersperse("\n\n\n")

  def ofChapter(study: Study, flags: WithFlags)(chapter: Chapter) = {
    val tags  = makeTags(study, chapter)
    val moves = toMoves(chapter.root)(flags).toList
    val initial = Initial(
      renderComments(chapter.root.comments, chapter.root.hasMultipleCommentAuthors) ::: shapeComment(
        chapter.root.shapes
      ).toList
    )
    if (flags.csa) Csa(tags, moves, initial) else Kif(tags, moves, initial)
  }

  private val fileR = """[\s,]""".r

  def ownerName(study: Study) = lightUserApi.sync(study.ownerId).fold(study.ownerId)(_.name)

  def filename(study: Study): String = {
    val date = dateFormat.print(study.createdAt)
    java.net.URLEncoder.encode(
      fileR.replaceAllIn(
        s"lishogi_study_${slugify(study.name.value)}_by_${ownerName(study)}_${date}",
        ""
      ),
      "UTF-8"
    )
  }

  def filename(study: Study, chapter: Chapter): String = {
    val date = dateFormat.print(chapter.createdAt)
    java.net.URLEncoder.encode(
      fileR.replaceAllIn(
        s"lishogi_study_${slugify(study.name.value)}_${slugify(chapter.name.value)}_by_${ownerName(study)}_${date}",
        ""
      ),
      "UTF-8"
    )
  }

  private def chapterUrl(studyId: Study.Id, chapterId: Chapter.Id) =
    s"${net.baseUrl}/study/$studyId/$chapterId"

  private val dateFormat = DateTimeFormat forPattern "yyyy.MM.dd"

  private def makeTags(study: Study, chapter: Chapter): Tags =
    Tags {
      val opening = chapter.opening
      val genTags = List(
        Tag(_.Event, s"${study.name} - ${chapter.name}"),
        Tag(_.Site, chapterUrl(study.id, chapter.id)),
        Tag(_.Annotator, ownerName(study))
      ) ::: (!Forsyth.compareTruncated(chapter.root.fen.value, Forsyth.initial)).??(
        List(
          Tag(_.FEN, chapter.root.fen.value)
        )
      )
      opening
        .fold(genTags)(o => Tag(_.Opening, o.eco) :: genTags)
        .foldLeft(chapter.tags.value.reverse) { case (tags, tag) =>
          if (tags.exists(t => tag.name == t.name)) tags
          else tag :: tags
        }
        .reverse
    }
}

object NotationDump {

  case class WithFlags(csa: Boolean, comments: Boolean, variations: Boolean, clocks: Boolean)

  private type Variations = Vector[Node]
  private val noVariations: Variations = Vector.empty

  private def node2move(node: Node, variations: Variations, startingPly: Int, showAuthors: Boolean)(implicit
      flags: WithFlags
  ) =
    NotationMove(
      san = node.move.san,
      uci = node.move.uci,
      ply = node.ply - startingPly,
      glyphs = if (flags.comments) node.glyphs else Glyphs.empty,
      comments = flags.comments ?? {
        renderComments(node.comments, showAuthors) ::: shapeComment(node.shapes).toList
      },
      result = none,
      variations = flags.variations ?? {
        variations.view.map { child =>
          toMoves(child.mainline, noVariations, startingPly, showAuthors).toList
        }.toList
      }
      //secondsSpent = flags.clocks ?? node.clock.map(_.roundSeconds)
      //secondsTotal = flags.clocks ?? node.clock.map(_.roundSeconds)
    )

  private def shapeComment(shapes: Shapes): Option[String] = {
    def render(as: String)(shapes: List[String]) =
      shapes match {
        case Nil    => ""
        case shapes => s"[%$as ${shapes.mkString(",")}]"
      }
    val circles = render("csl") {
      shapes.value.collect { case Shape.Circle(brush, orig) =>
        s"${brush.head.toUpper}${orig.usiKey}"
      }
    }
    val arrows = render("cal") {
      shapes.value.collect { case Shape.Arrow(brush, orig, dest) =>
        s"${brush.head.toUpper}${orig.usiKey}${dest.usiKey}"
      }
    }
    val pieces = render("cpl") {
      shapes.value.collect { case Shape.Piece(brush, orig, piece) =>
        s"${brush.head.toUpper}${orig.usiKey}${piece.forsyth}"
      }
    }
    s"$circles$arrows$pieces".some.filter(_.nonEmpty)
  }

  private def renderComments(comments: Comments, showAuthors: Boolean): List[String] = {
    def getName(author: Comment.Author) = {
      val nameOpt = author match {
        case Comment.Author.User(_, id)  => id.some
        case Comment.Author.External(id) => id.some
        case Comment.Author.Lishogi      => "lishogi".some
        case Comment.Author.Unknown      => none
      }
      nameOpt.filter(_.nonEmpty).map(n => s"[${n}] ").getOrElse("")
    }
    comments.list.map(c => s"${showAuthors ?? s"${getName(c.by)}"}${c.text.value}")
  }

  def toMoves(root: Node.Root)(implicit flags: WithFlags): Vector[NotationMove] =
    toMoves(root.mainline, root.children.variations, root.ply, root.hasMultipleCommentAuthors)

  def toMoves(
      line: Vector[Node],
      variations: Variations,
      startingPly: Int,
      showAuthors: Boolean
  )(implicit flags: WithFlags): Vector[NotationMove] =
    line
      .foldLeft(variations -> Vector.empty[NotationMove]) { case variations ~ moves ~ first =>
        first.children.variations -> (node2move(first, variations, startingPly, showAuthors) +: moves)
      }
      ._2
      .reverse
}
