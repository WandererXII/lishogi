package lila.study

import shogi.format.forsyth.Sfen
import shogi.format.usi.Usi
import shogi.{ Piece, Pos }
import play.api.libs.json._
import scala.util.chaining._

import lila.common.Json._
import lila.socket.Socket.Sri
import lila.tree.Node.Shape
import lila.user.User

final class JsonView(
    studyRepo: StudyRepo,
    lightUserApi: lila.user.LightUserApi
)(implicit ec: scala.concurrent.ExecutionContext) {

  import JsonView._

  def apply(
      study: Study,
      chapters: List[Chapter.Metadata],
      currentChapter: Chapter,
      me: Option[User]
  ) = {

    def allowed(selection: Settings.UserSelection): Boolean =
      Settings.UserSelection.allows(selection, study, me.map(_.id))

    me.?? { studyRepo.liked(study, _) } map { liked =>
      studyWrites.writes(study) ++ Json
        .obj(
          "liked" -> liked,
          "features" -> Json.obj(
            "cloneable"   -> allowed(study.settings.cloneable),
            "chat"        -> allowed(study.settings.chat),
            "sticky"      -> study.settings.sticky,
            "description" -> study.settings.description
          ),
          "topics"   -> study.topicsOrEmpty,
          "chapters" -> chapters.map(chapterMetadataWrites.writes),
          "chapter" -> Json
            .obj(
              "id"      -> currentChapter.id,
              "ownerId" -> currentChapter.ownerId,
              "setup"   -> currentChapter.setup,
              "tags"    -> currentChapter.tags,
              "features" -> Json.obj(
                "computer" -> allowed(study.settings.computer),
                "explorer" -> allowed(study.settings.explorer)
              )
            )
            .add("description", currentChapter.description)
            .add("serverEval", currentChapter.serverEval)
            .add("relay", currentChapter.relay)(relayWrites)
            .pipe(addChapterMode(currentChapter))
        )
        .add("description", study.description)
    }
  }

  def chapterConfig(c: Chapter) =
    Json
      .obj(
        "id"          -> c.id,
        "name"        -> c.name,
        "orientation" -> c.setup.orientation
      )
      .add("description", c.description) pipe addChapterMode(c)

  def pagerData(s: Study.WithChaptersAndLiked) =
    Json.obj(
      "id"        -> s.study.id.value,
      "name"      -> s.study.name.value,
      "liked"     -> s.liked,
      "likes"     -> s.study.likes.value,
      "updatedAt" -> s.study.updatedAt,
      "owner"     -> lightUserApi.sync(s.study.ownerId),
      "chapters"  -> s.chapters.take(4),
      "members"   -> s.study.members.members.values.take(4)
    )

  private def addChapterMode(c: Chapter)(js: JsObject): JsObject =
    js.add("practice", c.isPractice)
      .add("gamebook", c.isGamebook)
      .add("conceal", c.conceal)

  implicit private[study] val memberRoleWrites = Writes[StudyMember.Role] { r =>
    JsString(r.id)
  }
  implicit private[study] val memberWrites: Writes[StudyMember] = Writes[StudyMember] { m =>
    Json.obj("user" -> lightUserApi.syncFallback(m.id), "role" -> m.role)
  }

  implicit private[study] val membersWrites: Writes[StudyMembers] = Writes[StudyMembers] { m =>
    Json toJson m.members
  }

  implicit private val studyWrites = OWrites[Study] { s =>
    Json
      .obj(
        "id"                 -> s.id,
        "name"               -> s.name,
        "members"            -> s.members,
        "position"           -> s.position,
        "ownerId"            -> s.ownerId,
        "settings"           -> s.settings,
        "visibility"         -> s.visibility,
        "createdAt"          -> s.createdAt,
        "secondsSinceUpdate" -> (nowSeconds - s.updatedAt.getSeconds).toInt,
        "from"               -> s.from,
        "likes"              -> s.likes
      )
      .add("isNew" -> s.isNew)
  }
}

object JsonView {

  case class JsData(study: JsObject, analysis: JsObject)

  implicit val studyIdWrites: Writes[Study.Id]     = stringIsoWriter(Study.idIso)
  implicit val studyNameWrites: Writes[Study.Name] = stringIsoWriter(Study.nameIso)
  implicit val studyIdNameWrites = OWrites[Study.IdName] { s =>
    Json.obj("id" -> s._id, "name" -> s.name)
  }
  implicit val chapterIdWrites: Writes[Chapter.Id]     = stringIsoWriter(Chapter.idIso)
  implicit val chapterNameWrites: Writes[Chapter.Name] = stringIsoWriter(Chapter.nameIso)

  implicit private[study] val usiWrites: Writes[Usi] = Writes[Usi] { u =>
    JsString(u.usi)
  }
  implicit private val posReader: Reads[Pos] = Reads[Pos] { v =>
    (v.asOpt[String] flatMap Pos.fromKey).fold[JsResult[Pos]](JsError(Nil))(JsSuccess(_))
  }
  implicit private val colorReader: Reads[shogi.Color] = Reads[shogi.Color] { c =>
    (c.asOpt[String] flatMap shogi.Color.fromName)
      .fold[JsResult[shogi.Color]](JsError(Nil))(JsSuccess(_))
  }
  implicit private val roleReader: Reads[shogi.Role] = Reads[shogi.Role] { v =>
    (v.asOpt[String] flatMap { r => shogi.Role.allByName.get(r) })
      .fold[JsResult[shogi.Role]](JsError(Nil))(JsSuccess(_))
  }
  implicit private val pieceReader = Json.reads[Piece]

  implicit private[study] val pathWrites: Writes[Path] = Writes[Path] { p =>
    JsString(p.toString)
  }
  implicit private[study] val colorWriter: Writes[shogi.Color] = Writes[shogi.Color] { c =>
    JsString(c.name)
  }
  implicit private[study] val sfenWriter: Writes[Sfen] = Writes[Sfen] { f =>
    JsString(f.value)
  }
  implicit private[study] val sriWriter: Writes[Sri] = Writes[Sri] { sri =>
    JsString(sri.value)
  }
  implicit private[study] val visibilityWriter: Writes[Study.Visibility] = Writes[Study.Visibility] { v =>
    JsString(v.key)
  }
  implicit private[study] val fromWriter: Writes[Study.From] = Writes[Study.From] {
    case Study.From.Scratch   => JsString("scratch")
    case Study.From.Game(id)  => Json.obj("game" -> id)
    case Study.From.Study(id) => Json.obj("study" -> id)
    case Study.From.Relay(id) => Json.obj("relay" -> id)
  }
  implicit private[study] val userSelectionWriter = Writes[Settings.UserSelection] { v =>
    JsString(v.key)
  }
  implicit private[study] val settingsWriter: Writes[Settings] = Json.writes[Settings]

  implicit private[study] val pieceOrPosReader: Reads[Shape.PosOrPiece] = Reads[Shape.PosOrPiece] { v =>
    posReader.reads(v) match {
      case JsSuccess(pos, _) => JsSuccess(Left(pos).withRight[Piece])
      case JsError(_) =>
        pieceReader.reads(v) match {
          case JsSuccess(piece, _) => JsSuccess(Right(piece).withLeft[Pos])
          case JsError(_)          => JsError(Nil)
        }
    }
  }

  implicit private[study] val shapeReader: Reads[Shape] = Reads[Shape] { js =>
    js.asOpt[JsObject]
      .flatMap { o =>
        for {
          brush <- o str "brush"
          orig  <- o.get[Shape.PosOrPiece]("orig")
        } yield o.get[Shape.PosOrPiece]("dest") match {
          case Some(dest) if dest != orig => Shape.Arrow(brush, orig, dest)
          case _                          => Shape.Circle(brush, orig, o.get[Piece]("piece"))
        }
      }
      .fold[JsResult[Shape]](JsError(Nil))(JsSuccess(_))
  }
  implicit private val plyWrites = Writes[Chapter.Ply] { p =>
    JsNumber(p.value)
  }

  implicit val variantWrites = OWrites[shogi.variant.Variant] { v =>
    Json.obj("key" -> v.key, "name" -> v.name)
  }
  implicit val kifTagWrites: Writes[shogi.format.Tag] = Writes[shogi.format.Tag] { t =>
    Json.arr(t.name.kifName, t.value)
  }
  implicit val kifTagsWrites = Writes[shogi.format.Tags] { tags =>
    JsArray(tags.value map kifTagWrites.writes)
  }
  implicit private val chapterSetupWrites = Json.writes[Chapter.Setup]
  implicit private[study] val chapterMetadataWrites = OWrites[Chapter.Metadata] { c =>
    Json.obj("id" -> c._id, "name" -> c.name, "variant" -> c.setup.variant.key)
  }

  implicit private[study] val positionRefWrites: Writes[Position.Ref] = Json.writes[Position.Ref]
  implicit private val likesWrites: Writes[Study.Likes] = Writes[Study.Likes] { p =>
    JsNumber(p.value)
  }
  implicit private[study] val likingRefWrites: Writes[Study.Liking] = Json.writes[Study.Liking]

  implicit val relayWrites = OWrites[Chapter.Relay] { r =>
    Json.obj(
      "path"                 -> r.path,
      "secondsSinceLastMove" -> r.secondsSinceLastMove
    )
  }

  implicit private[study] val serverEvalWrites: Writes[Chapter.ServerEval] = Json.writes[Chapter.ServerEval]

  implicit private[study] val whoWriter: Writes[actorApi.Who] = Writes[actorApi.Who] { w =>
    Json.obj("u" -> w.u, "s" -> w.sri)
  }

  implicit val topicWrites: Writes[StudyTopic] = stringIsoWriter(StudyTopic.topicIso)
  implicit val topicsWrites: Writes[StudyTopics] = Writes[StudyTopics] { topics =>
    JsArray(topics.value map topicWrites.writes)
  }
}
