package lila.study

import scala.util.Success

import org.joda.time.DateTime
import reactivemongo.api.bson._

import shogi.Centis
import shogi.Piece
import shogi.Pos
import shogi.Status
import shogi.format.Glyph
import shogi.format.Glyphs
import shogi.format.Tag
import shogi.format.Tags
import shogi.format.forsyth.Sfen
import shogi.format.forsyth.SfenUtils
import shogi.format.usi.UciToUsi
import shogi.format.usi.Usi
import shogi.format.usi.UsiCharPair
import shogi.variant.Standard
import shogi.variant.Variant

import lila.common.Iso
import lila.db.BSON
import lila.db.BSON.Reader
import lila.db.BSON.Writer
import lila.db.dsl._
import lila.tree.Eval
import lila.tree.Eval.Score
import lila.tree.Node.Comment
import lila.tree.Node.Comments
import lila.tree.Node.Gamebook
import lila.tree.Node.Shape
import lila.tree.Node.Shapes

object BSONHandlers {

  import Chapter._

  implicit val StudyIdBSONHandler: BSONHandler[Study.Id]     = stringIsoHandler(Study.idIso)
  implicit val StudyNameBSONHandler: BSONHandler[Study.Name] = stringIsoHandler(Study.nameIso)
  implicit val ChapterIdBSONHandler: BSONHandler[Id]         = stringIsoHandler(Chapter.idIso)
  implicit val ChapterNameBSONHandler: BSONHandler[Name]     = stringIsoHandler(Chapter.nameIso)
  implicit val CentisBSONHandler: BSONHandler[Centis]        = intIsoHandler(Iso.centisIso)
  implicit val StudyTopicBSONHandler: BSONHandler[StudyTopic] = stringIsoHandler(
    StudyTopic.topicIso,
  )
  implicit val StudyTopicsBSONHandler: BSONHandler[StudyTopics] =
    implicitly[BSONHandler[List[StudyTopic]]].as[StudyTopics](StudyTopics.apply, _.value)

  implicit private val PieceBSONHandler: BSONHandler[Piece] = tryHandler[Piece](
    { case BSONString(v) => SfenUtils.toPiece(v, Standard) toTry s"No such piece: $v" },
    x => BSONString(SfenUtils.toForsyth(x, Standard).getOrElse("p")),
  )

  implicit private val PosOrPieceBSONHandler: BSONHandler[Shape.PosOrPiece] =
    tryHandler[Shape.PosOrPiece](
      { case BSONString(v) =>
        Pos
          .fromKey(v)
          .map(Left(_).withRight[Piece])
          .orElse(
            SfenUtils.toPiece(v, Standard).map(Right(_).withLeft[Pos]),
          ) toTry s"No such pos or piece: $v"
      },
      x => BSONString(x.fold(_.key, p => SfenUtils.toForsyth(p, Standard).getOrElse("p"))),
    )

  implicit val ShapeBSONHandler: BSON[Shape] = new BSON[Shape] {
    def reads(r: Reader) = {
      val brush = r str "b"
      r.getO[Shape.PosOrPiece]("p") map { pos =>
        Shape.Circle(brush, pos, None)
      } getOrElse {
        r.getO[Shape.PosOrPiece]("d") map { dest =>
          Shape.Arrow(brush, r.get[Shape.PosOrPiece]("o"), dest)
        } getOrElse Shape.Circle(brush, r.get[Shape.PosOrPiece]("o"), r.getO[Piece]("k"))
      }
    }
    def writes(w: Writer, t: Shape) =
      t match {
        case Shape.Circle(brush, pop, None) => $doc("b" -> brush, "p" -> pop)
        case Shape.Circle(brush, pop, Some(piece)) =>
          $doc("b" -> brush, "o" -> pop, "k" -> SfenUtils.toForsyth(piece, Standard).getOrElse("P"))
        case Shape.Arrow(brush, origPop, destPop) =>
          $doc("b" -> brush, "o" -> origPop, "d" -> destPop)
      }
  }

  implicit val UsiHandler: BSONHandler[Usi] = tryHandler[Usi](
    { case BSONString(v) => Usi(v).orElse(UciToUsi(v)) toTry s"Bad USI: $v" },
    x => BSONString(x.usi),
  )

  implicit val UsiCharPairHandler: BSONHandler[UsiCharPair] = tryHandler[UsiCharPair](
    { case BSONString(v) =>
      v.toArray match {
        case Array(a, b) => Success(UsiCharPair(a, b))
        case _           => handlerBadValue(s"Invalid UsiCharPair $v")
      }
    },
    x => BSONString(x.toString),
  )

  import Study.IdName
  implicit val StudyIdNameBSONHandler: BSONDocumentHandler[IdName] = Macros.handler[IdName]

  implicit val ShapesBSONHandler: BSONHandler[Shapes] =
    isoHandler[Shapes, List[Shape]]((s: Shapes) => s.value, Shapes(_))

  implicit private val CommentIdBSONHandler: BSONHandler[Comment.Id] =
    stringAnyValHandler[Comment.Id](_.value, Comment.Id.apply)
  implicit private val CommentTextBSONHandler: BSONHandler[Comment.Text] =
    stringAnyValHandler[Comment.Text](_.value, Comment.Text.apply)
  implicit val CommentAuthorBSONHandler: BSONHandler[Comment.Author] = quickHandler[Comment.Author](
    {
      case BSONString(lila.user.User.lishogiId | "l") => Comment.Author.Lishogi
      case BSONString(name)                           => Comment.Author.External(name)
      case doc: Bdoc =>
        {
          for {
            id   <- doc.getAsOpt[String]("id")
            name <- doc.getAsOpt[String]("name")
          } yield Comment.Author.User(id, name)
        } err s"Invalid comment author $doc"
      case _ => Comment.Author.Unknown
    },
    {
      case Comment.Author.User(id, name) => $doc("id" -> id, "name" -> name)
      case Comment.Author.External(name) => BSONString(s"${name.trim}")
      case Comment.Author.Lishogi        => BSONString("l")
      case Comment.Author.Unknown        => BSONString("")
    },
  )
  implicit private val CommentBSONHandler: BSONDocumentHandler[Comment] = Macros.handler[Comment]

  implicit val CommentsBSONHandler: BSONHandler[Comments] =
    isoHandler[Comments, List[Comment]]((s: Comments) => s.value, Comments(_))

  implicit val GamebookBSONHandler: BSONDocumentHandler[Gamebook] = Macros.handler[Gamebook]

  implicit val GlyphsBSONHandler: BSONHandler[Glyphs] = {
    val intReader = collectionReader[List, Int]
    tryHandler[Glyphs](
      { case arr: Barr =>
        intReader.readTry(arr) map { ints =>
          Glyphs.fromList(ints flatMap Glyph.find)
        }
      },
      x => BSONArray(x.toList.map(_.id).map(BSONInteger.apply)),
    )
  }

  implicit val EvalScoreBSONHandler: BSONHandler[Score] = {
    val mateFactor = 1000000
    BSONIntegerHandler.as[Score](
      v =>
        Score {
          if (v >= mateFactor || v <= -mateFactor) Right(Eval.Mate(v / mateFactor))
          else Left(Eval.Cp(v))
        },
      _.value.fold(
        cp => cp.value atLeast (-mateFactor + 1) atMost (mateFactor - 1),
        mate => mate.value * mateFactor,
      ),
    )
  }

  implicit val VariantBSONHandler: BSONHandler[Variant] = quickHandler[Variant](
    { case BSONInteger(v) => Variant.orDefault(v) },
    x => BSONInteger(x.id),
  )

  def readNode(doc: Bdoc, id: UsiCharPair): Option[Node] = {
    import Node.{ BsonFields => F }
    for {
      ply  <- doc.getAsOpt[Int](F.ply)
      usi  <- doc.getAsOpt[Usi](F.usi)
      sfen <- doc.getAsOpt[Sfen](F.sfen)
      check          = ~doc.getAsOpt[Boolean](F.check)
      shapes         = doc.getAsOpt[Shapes](F.shapes) getOrElse Shapes.empty
      comments       = doc.getAsOpt[Comments](F.comments) getOrElse Comments.empty
      gamebook       = doc.getAsOpt[Gamebook](F.gamebook)
      glyphs         = doc.getAsOpt[Glyphs](F.glyphs) getOrElse Glyphs.empty
      score          = doc.getAsOpt[Score](F.score)
      clock          = doc.getAsOpt[Centis](F.clock)
      forceVariation = ~doc.getAsOpt[Boolean](F.forceVariation)
    } yield Node(
      id,
      ply,
      usi,
      sfen,
      check,
      shapes,
      comments,
      gamebook,
      glyphs,
      score,
      clock,
      Node.emptyChildren,
      forceVariation,
    )
  }

  def writeNode(n: Node) = {
    import Node.BsonFields._
    val w = new Writer
    $doc(
      ply            -> n.ply,
      usi            -> n.usi,
      sfen           -> n.sfen,
      check          -> w.boolO(n.check),
      shapes         -> n.shapes.value.nonEmpty.option(n.shapes),
      comments       -> n.comments.value.nonEmpty.option(n.comments),
      gamebook       -> n.gamebook,
      glyphs         -> n.glyphs.nonEmpty,
      score          -> n.score,
      clock          -> n.clock,
      forceVariation -> w.boolO(n.forceVariation),
      order -> {
        (n.children.nodes.sizeIs > 1) option n.children.nodes.map(_.id)
      },
    )
  }

  def readGameMainlineExtension(doc: Bdoc): Node.GameMainlineExtension = {
    import Node.{ BsonFields => F }
    Node.GameMainlineExtension(
      shapes = doc.getAsOpt[Shapes](F.shapes) getOrElse Shapes.empty,
      comments = doc.getAsOpt[Comments](F.comments) getOrElse Comments.empty,
      glyphs = doc.getAsOpt[Glyphs](F.glyphs) getOrElse Glyphs.empty,
      score = doc.getAsOpt[Score](F.score),
    )
  }

  def writeGameMainlineExtension(rn: RootOrNode): Bdoc = {
    import Node.BsonFields._
    $doc(
      shapes   -> rn.shapes.value.nonEmpty.option(rn.shapes),
      comments -> rn.comments.value.nonEmpty.option(rn.comments),
      glyphs   -> rn.glyphs.nonEmpty,
      score    -> rn.score,
      order -> {
        (rn.children.nodes.sizeIs > 1) option rn.children.nodes.map(_.id)
      },
    )
  }

  import Node.Root
  implicit private[study] lazy val NodeRootBSONHandler: BSON[Root] = new BSON[Root] {
    import Node.{ BsonFields => F }
    def reads(fullReader: Reader) = {
      val rootNode = fullReader.doc.getAsOpt[Bdoc](Path.rootDbKey)
      rootNode.fold {
        val gameMainlineEl =
          fullReader.doc.getAsOpt[Bdoc](Path.gameMainlineDbKey) err "Missing root"
        val r       = new Reader(gameMainlineEl)
        val variant = r.get[Variant](F.variant)
        val gm = Node.GameMainline(
          id = r strD F.gameId,
          part = r intD F.part,
          variant = variant,
          usis = shogi.format.usi.Binary.decode(
            (r bytesD F.usis).value.toList,
            variant,
            Node.MAX_PLIES,
          ),
          initialSfen = r.getO[Sfen](F.initialSfen),
          clocks = r.getO[Vector[Centis]](F.clocks),
        )
        StudyFlatTree.reader.mergeGameRoot(GameRootCache(gm), fullReader.doc)
      } { rn =>
        val r = new Reader(rn)
        Root(
          sfen = r.get[Sfen](F.sfen),
          ply = r int F.ply,
          check = r boolD F.check,
          shapes = r.getO[Shapes](F.shapes) | Shapes.empty,
          comments = r.getO[Comments](F.comments) | Comments.empty,
          gamebook = r.getO[Gamebook](F.gamebook),
          glyphs = r.getO[Glyphs](F.glyphs) | Glyphs.empty,
          score = r.getO[Score](F.score),
          clock = r.getO[Centis](F.clock),
          gameMainline = none,
          children = StudyFlatTree.reader.rootChildren(fullReader.doc),
        )
      }
    }
    def writes(w: Writer, r: Root) = $doc(
      r.gameMainline.fold {
        StudyFlatTree.writer.rootChildren(r) appended {
          Path.rootDbKey -> $doc(
            F.ply      -> r.ply,
            F.sfen     -> r.sfen,
            F.check    -> r.check.some.filter(identity),
            F.shapes   -> r.shapes.value.nonEmpty.option(r.shapes),
            F.comments -> r.comments.value.nonEmpty.option(r.comments),
            F.gamebook -> r.gamebook,
            F.glyphs   -> r.glyphs.nonEmpty,
            F.score    -> r.score,
            F.clock    -> r.clock,
          )
        }
      } { gm =>
        StudyFlatTree.writer.gameRoot(r) appended {
          Path.gameMainlineDbKey -> $doc(
            F.gameId  -> gm.id,
            F.part    -> gm.part.some.filter(_ > 0),
            F.variant -> gm.variant,
            F.usis -> lila.db.ByteArray {
              shogi.format.usi.Binary.encode(gm.usis, gm.variant)
            },
            F.initialSfen -> gm.initialSfen,
            F.clocks      -> gm.clocks,
          )
        }
      },
    )
  }

  implicit val PathBSONHandler: BSONHandler[Path] =
    BSONStringHandler.as[Path](Path.apply, _.toString)

  implicit val TagBSONHandler: BSONHandler[Tag] = tryHandler[Tag](
    { case BSONString(v) =>
      v.split(":", 2) match {
        case Array(name, value) => Success(Tag(name, value))
        case _                  => handlerBadValue(s"Invalid tag $v")
      }
    },
    t => BSONString(s"${t.name}:${t.value}"),
  )
  implicit val tagsHandler: BSONHandler[Tags] =
    implicitly[BSONHandler[List[Tag]]].as[Tags](Tags.apply, _.value)
  implicit val StatusBSONHandler: BSONHandler[Status] = quickHandler[shogi.Status](
    { case BSONInteger(v) => shogi.Status(v).getOrElse(shogi.Status.UnknownFinish) },
    x => BSONInteger(x.id),
  )
  implicit private val ChapterEndStatusBSONHandler: BSONDocumentHandler[EndStatus] =
    Macros.handler[Chapter.EndStatus]
  implicit private val ChapterSetupBSONHandler: BSONDocumentHandler[Setup] =
    Macros.handler[Chapter.Setup]
  implicit val ChapterServerEvalBSONHandler: BSONDocumentHandler[ServerEval] =
    Macros.handler[Chapter.ServerEval]
  import Chapter.Ply
  implicit val PlyBSONHandler: BSONHandler[Ply] = intAnyValHandler[Ply](_.value, Ply.apply)
  implicit val ChapterBSONHandler: BSONDocumentHandler[Chapter] = Macros.handler[Chapter]
  implicit val ChapterMetadataBSONHandler: BSONDocumentHandler[Metadata] =
    Macros.handler[Chapter.Metadata]

  implicit val PositionRefBSONHandler: BSONHandler[Position.Ref] = quickHandler[Position.Ref](
    { case BSONString(v) => Position.Ref.decode(v) },
    x => BSONString(x.encode),
  )
  implicit val StudyMemberRoleBSONHandler: BSONHandler[StudyMember.Role] =
    tryHandler[StudyMember.Role](
      { case BSONString(v) => StudyMember.Role.byId get v toTry s"Invalid role $v" },
      x => BSONString(x.id),
    )
  private case class DbMember(role: StudyMember.Role) extends AnyVal
  implicit private val DbMemberBSONHandler: BSONDocumentHandler[DbMember] = Macros.handler[DbMember]
  implicit private[study] val StudyMemberBSONWriter: BSONWriter[StudyMember] =
    new BSONWriter[StudyMember] {
      def writeTry(x: StudyMember) = DbMemberBSONHandler writeTry DbMember(x.role)
    }
  implicit private[study] val MembersBSONHandler: BSONHandler[StudyMembers] =
    implicitly[BSONHandler[Map[String, DbMember]]].as[StudyMembers](
      members =>
        StudyMembers(members map { case (id, dbMember) =>
          id -> StudyMember(id, dbMember.role)
        }),
      _.members.view.mapValues(m => DbMember(m.role)).toMap,
    )
  import Study.Visibility
  implicit private[study] val VisibilityHandler: BSONHandler[Visibility] = tryHandler[Visibility](
    { case BSONString(v) => Visibility.byKey get v toTry s"Invalid visibility $v" },
    v => BSONString(v.key),
  )

  implicit val GamePlayerBSONHandler: BSONHandler[Study.GamePlayer] =
    quickHandler[Study.GamePlayer](
      { case BSONString(str) =>
        Study.GamePlayer(str.take(4), str.drop(4).some.filter(_.nonEmpty))
      },
      x => BSONString(s"${x.playerId}${~x.userId}"),
    )
  import Study.PostGameStudy
  implicit private val PostGameStudyBSONHandler: BSONDocumentHandler[PostGameStudy] =
    Macros.handler[PostGameStudy]

  import Study.From
  implicit private[study] val FromHandler: BSONHandler[From] = quickHandler[From](
    { case BSONString(v) =>
      v.split(' ') match {
        case Array("scratch")   => From.Scratch
        case Array("game", id)  => From.Game(id)
        case Array("study", id) => From.Study(Study.Id(id))
        case _                  => From.Unknown
      }
    },
    x =>
      BSONString(x match {
        case From.Scratch   => "scratch"
        case From.Game(id)  => s"game $id"
        case From.Study(id) => s"study $id"
        case From.Unknown   => "unknown"
      }),
  )
  import Settings.UserSelection
  implicit private[study] val UserSelectionHandler: BSONHandler[UserSelection] =
    tryHandler[UserSelection](
      { case BSONString(v) => UserSelection.byKey get v toTry s"Invalid user selection $v" },
      x => BSONString(x.key),
    )
  implicit val SettingsBSONHandler: BSON[Settings] = new BSON[Settings] {
    def reads(r: Reader) =
      Settings(
        computer = r.get[UserSelection]("computer"),
        cloneable = r.getO[UserSelection]("cloneable") | Settings.init.cloneable,
        chat = r.getO[UserSelection]("chat") | Settings.init.chat,
        sticky = r.getO[Boolean]("sticky") | Settings.init.sticky,
        description = r.getO[Boolean]("description") | Settings.init.description,
      )
    private val writer                 = Macros.writer[Settings]
    def writes(w: Writer, s: Settings) = writer.writeTry(s).get
  }

  import Study.Likes
  implicit val LikesBSONHandler: BSONHandler[Likes] = intAnyValHandler[Likes](_.value, Likes.apply)
  import Study.Rank
  implicit private[study] val RankBSONHandler: BSONHandler[Rank] =
    dateIsoHandler[Rank](Iso[DateTime, Rank](Rank.apply, _.value))

  import Study.MiniStudy
  implicit val StudyMiniBSONHandler: BSONDocumentHandler[MiniStudy] = Macros.handler[MiniStudy]

  implicit val StudyBSONHandler: BSONDocumentHandler[Study] = Macros.handler[Study]

}
