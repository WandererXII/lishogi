package lila.chat

import scala.concurrent.duration._

import org.joda.time.DateTime
import reactivemongo.api.ReadPreference

import shogi.Color

import lila.common.Bus
import lila.common.String.noShouting
import lila.common.config.NetDomain
import lila.db.dsl._
import lila.hub.actorApi.shutup.PublicSource
import lila.hub.actorApi.shutup.RecordPrivateChat
import lila.hub.actorApi.shutup.RecordPublicChat
import lila.memo.CacheApi._
import lila.user.User
import lila.user.UserRepo

final class ChatApi(
    coll: Coll,
    userRepo: UserRepo,
    chatTimeout: ChatTimeout,
    flood: lila.security.Flood,
    spam: lila.security.Spam,
    shutup: lila.hub.actors.Shutup,
    modActor: lila.hub.actors.Mod,
    cacheApi: lila.memo.CacheApi,
    maxLinesPerChat: Chat.MaxLines,
    netDomain: NetDomain,
)(implicit ec: scala.concurrent.ExecutionContext) {

  import Chat.chatBSONHandler
  import Chat.chatIdBSONHandler

  // only use for public, multi-user chats - tournaments, simuls
  object cached {
    private val cache = cacheApi[Chat.Id, Chat](128, "chat.user") {
      _.expireAfterAccess(1 minute)
        .buildAsyncFuture(find)
    }

    def invalidate = cache.invalidate _

    def findMine(chatId: Chat.Id, me: Option[User]): Fu[Chat.Mine] =
      me match {
        case Some(user) =>
          cache get chatId flatMap { chat =>
            (!chat.isEmpty ?? chatTimeout.isActive(chatId, user.id)) dmap {
              Chat.Mine(chat forUser user.some, _)
            }
          }
        case None => cache.get(chatId) dmap { Chat.Mine(_, false) }
      }
  }

  def findOption(chatId: Chat.Id): Fu[Option[Chat]] =
    coll.byId[Chat](chatId.value)

  def find(chatId: Chat.Id): Fu[Chat] =
    findOption(chatId) dmap (_ | Chat.make(chatId))

  def findAll(chatIds: List[Chat.Id]): Fu[List[Chat]] =
    coll.byIds[Chat](chatIds.map(_.value), ReadPreference.secondaryPreferred)

  def findMine(chatId: Chat.Id, me: Option[User]): Fu[Chat.Mine] =
    findMineIf(chatId, me, cond = true)

  def findMineIf(chatId: Chat.Id, me: Option[User], cond: Boolean): Fu[Chat.Mine] =
    me match {
      case Some(user) if cond =>
        find(chatId) flatMap { chat =>
          (!chat.isEmpty ?? chatTimeout.isActive(chatId, user.id)) dmap {
            Chat.Mine(chat forUser user.some, _)
          }
        }
      case Some(user)   => fuccess(Chat.Mine(Chat.make(chatId) forUser user.some, false))
      case None if cond => find(chatId) dmap { Chat.Mine(_, false) }
      case None         => fuccess(Chat.Mine(Chat.make(chatId), false))
    }

  def optionsByOrderedIds(chatIds: List[Chat.Id]): Fu[List[Option[Chat]]] =
    coll.optionsByOrderedIds[Chat, Chat.Id](
      chatIds,
      none,
      ReadPreference.secondaryPreferred,
    )(_.id)

  def write(
      chatId: Chat.Id,
      userId: User.ID,
      text: String,
      publicSource: Option[PublicSource],
      busChan: BusChan.Select,
      permanentChat: Boolean = false,
  ): Funit =
    makeUserLine(chatId, userId, text) flatMap {
      _ ?? { line =>
        pushLine(chatId, line, permanentChat) >>- {
          if (publicSource.isDefined) cached.invalidate(chatId)
          shutup ! {
            publicSource match {
              case Some(source) => RecordPublicChat(userId, text, source)
              case _            => RecordPrivateChat(chatId.value, userId, text)
            }
          }
          publish(chatId, actorApi.ChatLine(chatId, line), busChan)
          lila.mon.chat
            .message(publicSource.fold("player")(_.parentName), line.troll)
            .increment()
            .unit
        }
      }
    }

  def writeAnon(chatId: Chat.Id, color: Color, text: String, busChan: BusChan.Select): Funit =
    makeAnonLine(chatId, color, text) ?? { line =>
      pushLine(chatId, line) >>- {
        publish(chatId, actorApi.ChatLine(chatId, line), busChan)
        lila.mon.chat.message("anonPlayer", false).increment().unit
      }
    }

  def system(
      chatId: Chat.Id,
      text: String,
      busChan: BusChan.Select,
      volatile: Boolean = false,
  ): Funit = {
    val line = UserLine(systemUserId, none, text, troll = false, deleted = false)
    (!volatile ?? pushLine(chatId, line)) >>- {
      if (!volatile) cached.invalidate(chatId)
      publish(chatId, actorApi.ChatLine(chatId, line), busChan)
    }
  }

  def timeout(
      chatId: Chat.Id,
      modId: User.ID,
      userId: User.ID,
      reason: ChatTimeout.Reason,
      scope: ChatTimeout.Scope,
      text: String,
      busChan: BusChan.Select,
  ): Funit =
    coll.byId[Chat](chatId.value) zip userRepo.byId(modId) zip userRepo.byId(userId) flatMap {
      case ((Some(chat), Some(mod)), Some(user))
          if isMod(mod) || scope == ChatTimeout.Scope.Local =>
        doTimeout(chat, mod, user, reason, scope, text, busChan)
      case _ => funit
    }

  def userModInfo(username: String): Fu[Option[UserModInfo]] =
    userRepo named username flatMap {
      _ ?? { user =>
        chatTimeout.history(user, 20) dmap { UserModInfo(user, _).some }
      }
    }

  private def doTimeout(
      c: Chat,
      mod: User,
      user: User,
      reason: ChatTimeout.Reason,
      scope: ChatTimeout.Scope,
      text: String,
      busChan: BusChan.Select,
  ): Funit = {
    val line = c.hasRecentLine(user) option UserLine(
      username = systemUserId,
      title = None,
      text = s"${user.username} was timed out 10 minutes for ${reason.name}.",
      troll = false,
      deleted = false,
    )
    val c2   = c.markDeleted(user)
    val chat = line.fold(c2)(c2.add)
    coll.update.one($id(chat.id), chat).void >>
      chatTimeout.add(c, mod, user, reason, scope) >>- {
        cached.invalidate(chat.id)
        publish(chat.id, actorApi.OnTimeout(chat.id, user.id), busChan)
        line foreach { l =>
          publish(chat.id, actorApi.ChatLine(chat.id, l), busChan)
        }
        if (isMod(mod))
          modActor ! lila.hub.actorApi.mod.ChatTimeout(
            mod = mod.id,
            user = user.id,
            reason = reason.key,
            text = text,
          )
        else
          logger.info(s"${mod.username} times out ${user.username} in #${c.id} for ${reason.key}")
      }
  }

  def delete(c: Chat, user: User, busChan: BusChan.Select): Funit = {
    val chat = c.markDeleted(user)
    coll.update.one($id(chat.id), chat).void >>- {
      cached.invalidate(chat.id)
      publish(chat.id, actorApi.OnTimeout(chat.id, user.id), busChan)
    }
  }

  def clearInactive(id: Chat.Id, busChan: BusChan.Select): Funit =
    coll.update
      .one(
        $id(id) ++
          $doc(
            Chat.BSONFields.updatedAt $lt DateTime.now.minusMinutes(15),
            Chat.BSONFields.lines $exists true,
          ),
        $unset(Chat.BSONFields.lines),
      ) map { res =>
      (res.nModified > 0) ?? {
        cached.invalidate(id)
        publish(id, actorApi.OnClear(id), busChan)
      }
    }

  private def isMod(user: User) = lila.security.Granter(_.ChatTimeout)(user)

  def reinstate(list: List[ChatTimeout.Reinstate]) =
    list.foreach { r =>
      Bus.publish(actorApi.OnReinstate(Chat.Id(r.chat), r.user), BusChan.Global.chan)
    }

  private def publish(chatId: Chat.Id, msg: Any, busChan: BusChan.Select): Unit = {
    Bus.publish(msg, busChan(BusChan).chan)
    Bus.publish(msg, Chat chanOf chatId)
  }

  def remove(chatId: Chat.Id) = coll.delete.one($id(chatId)).void

  def removeAll(chatIds: List[Chat.Id]) = coll.delete.one($inIds(chatIds)).void

  private def pushLine(chatId: Chat.Id, line: Line, permanentChat: Boolean = false): Funit =
    coll.update
      .one(
        $id(chatId),
        $doc(
          "$push" -> $doc(
            Chat.BSONFields.lines -> $doc(
              "$each"  -> List(line),
              "$slice" -> -maxLinesPerChat.value,
            ),
          ),
        ) ++ (!permanentChat ?? $set(Chat.BSONFields.updatedAt -> DateTime.now)),
        upsert = true,
      )
      .void

  private def makeUserLine(
      chatId: Chat.Id,
      userId: String,
      t1: String,
  ): Fu[Option[UserLine]] =
    userRepo.speaker(userId) zip chatTimeout.isActive(chatId, userId) dmap {
      case (Some(user), false) if user.enabled =>
        Writer cut t1 flatMap { t2 =>
          (user.isBot || flood.allowMessage(s"$chatId/$userId", t2)) option {
            UserLine(
              user.username,
              user.title.map(_.value),
              Writer preprocessUserInput t2,
              troll = user.isTroll,
              deleted = false,
            )
          }
        }
      case _ => none
    }

  private def makeAnonLine(chatId: Chat.Id, color: Color, t1: String): Option[Line] =
    Writer cut t1 flatMap { t2 =>
      flood.allowMessage(s"$chatId/${color.letter}", t2) option
        AnonLine(color, Writer preprocessUserInput t2)
    }

  private object Writer {

    import java.util.regex.Matcher
    import java.util.regex.Pattern

    def preprocessUserInput(in: String) = multiline(spam.replace(noShouting(noPrivateUrl(in))))

    def cut(text: String) = Some(text.trim take Line.textMaxSize) filter (_.nonEmpty)

    private val gameUrlRegex   = (Pattern.quote(netDomain.value) + """\b/(\w{8})\w{4}\b""").r
    private val gameUrlReplace = Matcher.quoteReplacement(netDomain.value) + "/$1";
    private def noPrivateUrl(str: String): String = gameUrlRegex.replaceAllIn(str, gameUrlReplace)
    private val multilineRegex                    = """\n\n{2,}+""".r
    private def multiline(str: String)            = multilineRegex.replaceAllIn(str, """\n\n""")
  }
}
