package lila.user

import org.joda.time.DateTime

import lila.db.dsl._

case class Note(
    _id: String,
    from: User.ID,
    to: User.ID,
    text: String,
    mod: Boolean,
    dox: Boolean,
    date: DateTime,
) {
  def userIds            = List(from, to)
  def isFrom(user: User) = user.id == from
}

case class UserNotes(user: User, notes: List[Note])

final class NoteApi(
    userRepo: UserRepo,
    coll: Coll,
)(implicit
    ec: scala.concurrent.ExecutionContext,
    ws: play.api.libs.ws.WSClient,
) {

  import reactivemongo.api.bson._
  implicit private val noteBSONHandler: BSONDocumentHandler[Note] = Macros.handler[Note]

  def get(user: User, me: User, isMod: Boolean): Fu[List[Note]] =
    coll
      .find(
        $doc("to" -> user.id) ++ {
          if (isMod)
            $or(
              $doc("from" -> me.id),
              $doc("mod"  -> true),
            )
          else $doc("from" -> me.id)
        },
      )
      .sort($sort desc "date")
      .cursor[Note]()
      .list(20)

  def forMod(id: User.ID): Fu[List[Note]] =
    coll
      .find($doc("to" -> id, "mod" -> true))
      .sort($sort desc "date")
      .cursor[Note]()
      .list(20)

  def forMod(ids: List[User.ID]): Fu[List[Note]] =
    coll
      .find($doc("to" $in ids, "mod" -> true))
      .sort($sort desc "date")
      .cursor[Note]()
      .list(50)

  def write(to: User, text: String, from: User, modOnly: Boolean, dox: Boolean) = {

    val note = Note(
      _id = lila.common.ThreadLocalRandom nextString 8,
      from = from.id,
      to = to.id,
      text = text,
      mod = modOnly,
      dox = modOnly && (dox || Title.fromUrl.toJSAId(text).isDefined),
      date = DateTime.now,
    )

    coll.insert.one(note) >>-
      lila.common.Bus.publish(
        lila.hub.actorApi.user.Note(
          from = from.username,
          to = to.username,
          text = note.text,
          mod = modOnly,
        ),
        "userNote",
      )
  } >> {
    modOnly ?? Title.fromUrl(text) flatMap {
      _ ?? { userRepo.addTitle(to.id, _) }
    }
  }

  def lishogiWrite(to: User, text: String) =
    userRepo.lishogi flatMap {
      _ ?? {
        write(to, text, _, true, false)
      }
    }

  def byId(id: String): Fu[Option[Note]] = coll.byId[Note](id)

  def delete(id: String) = coll.delete.one($id(id))

  def erase(user: User) = coll.delete.one($doc("from" -> user.id))
}
