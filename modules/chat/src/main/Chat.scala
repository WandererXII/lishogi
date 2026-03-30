package lila.chat

import reactivemongo.api.bson.BSONHandler

import lila.common.Iso
import lila.user.User

case class Chat(
    id: Chat.Id,
    lines: List[Line],
) {

  def isEmpty  = lines.isEmpty
  def nonEmpty = lines.exists(_.isHuman)

  def forUser(u: Option[User]): Chat =
    if (u.??(_.marks.troll)) this
    else
      copy(lines = lines filter {
        case l: UserLine => !l.troll
        case _: AnonLine => true // Anon players don't have troll flags
      })

  def markDeleted(u: User) =
    copy(
      lines = lines.map {
        case l: UserLine if l.userId == u.id => l.delete
        case l                               => l
      },
    )

  def hasLinesOf(u: User) = lines.exists {
    case l: UserLine => l.userId == u.id
    case _           => false
  }

  def add(line: Line) = copy(lines = lines :+ line)

  def mapLines(f: Line => Line) = copy(lines = lines map f)

  def userIds: List[User.ID] = lines.collect { case l: UserLine => l.userId }

  def truncate(max: Int) = copy(lines = lines.drop((lines.size - max) atLeast 0))

  def hasRecentLine(u: User): Boolean = lines.reverse.take(12).exists {
    case l: UserLine => l.userId == u.id
    case _           => false
  }
}

object Chat {
  case class Id(value: String)         extends AnyVal with StringValue
  case class ResourceId(value: String) extends AnyVal with StringValue
  case class MaxLines(value: Int)      extends AnyVal with IntValue

  case class Mine(chat: Chat, timeout: Boolean) {
    def truncate(max: Int) = copy(chat = chat truncate max)
  }
  case class Game(chat: Chat, timeout: Boolean, restricted: Boolean)

  import lila.db.BSON

  def make(id: Chat.Id)   = Chat(id, Nil)
  def chanOf(id: Chat.Id) = s"chat:$id"

  object BSONFields {
    val id        = "_id"
    val lines     = "l"
    val updatedAt = "u"
  }

  import BSONFields._
  import Line.lineBSONHandler
  import reactivemongo.api.bson.BSONDocument

  implicit val chatIdIso: Iso.StringIso[Id]       = lila.common.Iso.string[Id](Id.apply, _.value)
  implicit val chatIdBSONHandler: BSONHandler[Id] = lila.db.BSON.stringIsoHandler(chatIdIso)

  implicit val chatBSONHandler: BSON[Chat] = new BSON[Chat] {
    def reads(r: BSON.Reader): Chat = {
      Chat(
        id = r.get[Id](id),
        lines = r.getD[List[Line]](lines),
      )
    }
    def writes(w: BSON.Writer, o: Chat) =
      BSONDocument(
        id    -> o.id,
        lines -> o.lines,
      )
  }
}
