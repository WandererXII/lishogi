package lila.chat

import play.api.libs.json._

import lila.common.Json._
import lila.common.LightUser

object JsonView {

  import writers._

  lazy val timeoutReasons = Json toJson ChatTimeout.Reason.all

  def apply(chat: Chat): JsValue = chatWriter writes chat

  def apply(line: Line): JsObject = lineWriter writes line

  def userModInfo(u: UserModInfo)(implicit lightUser: LightUser.GetterSync) =
    lila.user.JsonView.modWrites.writes(u.user) ++ Json.obj(
      "history" -> u.history,
    )

  def mobile(chat: Chat, writeable: Boolean = true) =
    Json.obj(
      "lines"     -> apply(chat),
      "writeable" -> writeable,
    )

  def boardApi(chat: Chat) = JsArray {
    chat.lines collect {
      case UserLine(name, _, text, troll, del) if !troll && !del =>
        Json.obj("text" -> text, "user" -> name)
    }
  }

  object writers {

    implicit val chatIdWrites: Writes[Chat.Id] = stringIsoWriter(Chat.chatIdIso)

    implicit val timeoutReasonWriter: Writes[ChatTimeout.Reason] = OWrites[ChatTimeout.Reason] {
      r =>
        Json.obj("key" -> r.key, "name" -> r.name)
    }

    implicit def timeoutEntryWriter(implicit
        lightUser: LightUser.GetterSync,
    ): OWrites[ChatTimeout.UserEntry] =
      OWrites[ChatTimeout.UserEntry] { e =>
        Json.obj(
          "reason" -> e.reason.key,
          "mod"    -> lightUser(e.mod).fold("?")(_.name),
          "date"   -> e.createdAt,
        )
      }

    implicit val chatWriter: Writes[Chat] = Writes[Chat] { c =>
      JsArray(c.lines map lineWriter.writes)
    }

    implicit private[chat] val lineWriter: OWrites[Line] = OWrites[Line] {
      case l: UserLine => userLineWriter writes l
      case l: AnonLine => anonLineWriter writes l
    }

    implicit private val userLineWriter: OWrites[UserLine] = OWrites[UserLine] { l =>
      Json
        .obj(
          "u" -> l.username,
          "t" -> l.text,
        )
        .add("r" -> l.troll)
        .add("d" -> l.deleted)
        .add("title" -> l.title)
    }

    implicit private val anonLineWriter: OWrites[AnonLine] = OWrites[AnonLine] { l =>
      Json.obj(
        "c" -> l.color.name,
        "t" -> l.text,
      )
    }
  }
}
