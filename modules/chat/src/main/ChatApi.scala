package lishogi.chat

import shogi.Color
import reactivemongo.api.ReadPreference
import scala.concurrent.duration._

import lishogi.common.config.NetDomain
import lishogi.common.String.noShouting
import lishogi.common.Bus
import lishogi.db.dsl._
import lishogi.hub.actorApi.shutup.{ PublicSource, RecordPrivateChat, RecordPublicChat }
import lishogi.memo.CacheApi._
import lishogi.user.{ User, UserRepo }

final class ChatApi(
    coll: Coll,
    userRepo: UserRepo,
    chatTimeout: ChatTimeout,
    flood: lishogi.security.Flood,
    spam: lishogi.security.Spam,
    shutup: lishogi.hub.actors.Shutup,
    modActor: lishogi.hub.actors.Mod,
    cacheApi: lishogi.memo.CacheApi,
    maxLinesPerChat: Chat.MaxLines,
    netDomain: NetDomain
)(implicit ec: scala.concurrent.ExecutionContext) {

  import Chat.{ chatIdBSONHandler, userChatBSONHandler }

  object userChat {

    // only use for public, multi-user chats - tournaments, simuls
    object cached {

      private val cache = cacheApi[Chat.Id, UserChat](128, "chat.user") {
        _.expireAfterAccess(1 minute)
          .buildAsyncFuture(find)
      }

      def invalidate = cache.invalidate _

      def findMine(chatId: Chat.Id, me: Option[User]): Fu[UserChat.Mine] =
        me match {
          case Some(user) => findMine(chatId, user)
          case None       => cache.get(chatId) dmap { UserChat.Mine(_, false) }
        }

      private def findMine(chatId: Chat.Id, me: User): Fu[UserChat.Mine] =
        cache get chatId flatMap { chat =>
          (!chat.isEmpty ?? chatTimeout.isActive(chatId, me.id)) dmap {
            UserChat.Mine(chat forUser me.some, _)
          }
        }
    }

    def findOption(chatId: Chat.Id): Fu[Option[UserChat]] =
      coll.byId[UserChat](chatId.value)

    def find(chatId: Chat.Id): Fu[UserChat] =
      findOption(chatId) dmap (_ | Chat.makeUser(chatId))

    def findAll(chatIds: List[Chat.Id]): Fu[List[UserChat]] =
      coll.byIds[UserChat](chatIds.map(_.value), ReadPreference.secondaryPreferred)

    def findMine(chatId: Chat.Id, me: Option[User]): Fu[UserChat.Mine] = findMineIf(chatId, me, true)

    def findMineIf(chatId: Chat.Id, me: Option[User], cond: Boolean): Fu[UserChat.Mine] =
      me match {
        case Some(user) if cond => findMine(chatId, user)
        case Some(user)         => fuccess(UserChat.Mine(Chat.makeUser(chatId) forUser user.some, false))
        case None if cond       => find(chatId) dmap { UserChat.Mine(_, false) }
        case None               => fuccess(UserChat.Mine(Chat.makeUser(chatId), false))
      }

    private def findMine(chatId: Chat.Id, me: User): Fu[UserChat.Mine] =
      find(chatId) flatMap { chat =>
        (!chat.isEmpty ?? chatTimeout.isActive(chatId, me.id)) dmap {
          UserChat.Mine(chat forUser me.some, _)
        }
      }

    def write(
        chatId: Chat.Id,
        userId: User.ID,
        text: String,
        publicSource: Option[PublicSource],
        busChan: BusChan.Select
    ): Funit =
      makeLine(chatId, userId, text) flatMap {
        _ ?? { line =>
          pushLine(chatId, line) >>- {
            if (publicSource.isDefined) cached invalidate chatId
            shutup ! {
              publicSource match {
                case Some(source) => RecordPublicChat(userId, text, source)
                case _            => RecordPrivateChat(chatId.value, userId, text)
              }
            }
            publish(chatId, actorApi.ChatLine(chatId, line), busChan)
            lishogi.mon.chat.message(publicSource.fold("player")(_.parentName), line.troll).increment()
          }
        }
      }

    def clear(chatId: Chat.Id) = coll.delete.one($id(chatId)).void

    def system(chatId: Chat.Id, text: String, busChan: BusChan.Select): Funit = {
      val line = UserLine(systemUserId, None, text, troll = false, deleted = false)
      pushLine(chatId, line) >>- {
        cached.invalidate(chatId)
        publish(chatId, actorApi.ChatLine(chatId, line), busChan)
      }
    }

    // like system, but not persisted.
    def volatile(chatId: Chat.Id, text: String, busChan: BusChan.Select): Unit = {
      val line = UserLine(systemUserId, None, text, troll = false, deleted = false)
      publish(chatId, actorApi.ChatLine(chatId, line), busChan)
    }

    def service(chatId: Chat.Id, text: String, busChan: BusChan.Select, isVolatile: Boolean): Unit =
      (if (isVolatile) volatile _ else system _)(chatId, text, busChan)

    def timeout(
        chatId: Chat.Id,
        modId: User.ID,
        userId: User.ID,
        reason: ChatTimeout.Reason,
        scope: ChatTimeout.Scope,
        text: String,
        busChan: BusChan.Select
    ): Funit =
      coll.byId[UserChat](chatId.value) zip userRepo.byId(modId) zip userRepo.byId(userId) flatMap {
        case Some(chat) ~ Some(mod) ~ Some(user) if isMod(mod) || scope == ChatTimeout.Scope.Local =>
          doTimeout(chat, mod, user, reason, scope, text, busChan)
        case _ => fuccess(none)
      }

    def userModInfo(username: String): Fu[Option[UserModInfo]] =
      userRepo named username flatMap {
        _ ?? { user =>
          chatTimeout.history(user, 20) dmap { UserModInfo(user, _).some }
        }
      }

    private def doTimeout(
        c: UserChat,
        mod: User,
        user: User,
        reason: ChatTimeout.Reason,
        scope: ChatTimeout.Scope,
        text: String,
        busChan: BusChan.Select
    ): Funit = {
      val line = c.hasRecentLine(user) option UserLine(
        username = systemUserId,
        title = None,
        text = s"${user.username} was timed out 10 minutes for ${reason.name}.",
        troll = false,
        deleted = false
      )
      val c2   = c.markDeleted(user)
      val chat = line.fold(c2)(c2.add)
      coll.update.one($id(chat.id), chat).void >>
        chatTimeout.add(c, mod, user, reason, scope) >>- {
          cached invalidate chat.id
          publish(chat.id, actorApi.OnTimeout(chat.id, user.id), busChan)
          line foreach { l =>
            publish(chat.id, actorApi.ChatLine(chat.id, l), busChan)
          }
          if (isMod(mod))
            modActor ! lishogi.hub.actorApi.mod.ChatTimeout(
              mod = mod.id,
              user = user.id,
              reason = reason.key,
              text = text
            )
          else logger.info(s"${mod.username} times out ${user.username} in #${c.id} for ${reason.key}")
        }
    }

    def delete(c: UserChat, user: User, busChan: BusChan.Select): Funit = {
      val chat = c.markDeleted(user)
      coll.update.one($id(chat.id), chat).void >>- {
        cached invalidate chat.id
        publish(chat.id, actorApi.OnTimeout(chat.id, user.id), busChan)
      }
    }

    private def isMod(user: User) = lishogi.security.Granter(_.ChatTimeout)(user)

    def reinstate(list: List[ChatTimeout.Reinstate]) =
      list.foreach { r =>
        Bus.publish(actorApi.OnReinstate(Chat.Id(r.chat), r.user), BusChan.Global.chan)
      }

    private[ChatApi] def makeLine(chatId: Chat.Id, userId: String, t1: String): Fu[Option[UserLine]] =
      userRepo.speaker(userId) zip chatTimeout.isActive(chatId, userId) dmap {
        case (Some(user), false) if user.enabled =>
          Writer cut t1 flatMap { t2 =>
            (user.isBot || flood.allowMessage(s"$chatId/$userId", t2)) option {
              UserLine(
                user.username,
                user.title.map(_.value),
                Writer preprocessUserInput t2,
                troll = user.isTroll,
                deleted = false
              )
            }
          }
        case _ => none
      }
  }

  object playerChat {

    def findOption(chatId: Chat.Id): Fu[Option[MixedChat]] =
      coll.byId[MixedChat](chatId.value)

    def find(chatId: Chat.Id): Fu[MixedChat] =
      findOption(chatId) dmap (_ | Chat.makeMixed(chatId))

    def findIf(chatId: Chat.Id, cond: Boolean): Fu[MixedChat] =
      if (cond) find(chatId)
      else fuccess(Chat.makeMixed(chatId))

    def findNonEmpty(chatId: Chat.Id): Fu[Option[MixedChat]] =
      findOption(chatId) dmap (_ filter (_.nonEmpty))

    def optionsByOrderedIds(chatIds: List[Chat.Id]): Fu[List[Option[MixedChat]]] =
      coll.optionsByOrderedIds[MixedChat, Chat.Id](chatIds, none, ReadPreference.secondaryPreferred)(_.id)

    def write(chatId: Chat.Id, color: Color, text: String, busChan: BusChan.Select): Funit =
      makeLine(chatId, color, text) ?? { line =>
        pushLine(chatId, line) >>- {
          publish(chatId, actorApi.ChatLine(chatId, line), busChan)
          lishogi.mon.chat.message("anonPlayer", false).increment()
        }
      }

    private def makeLine(chatId: Chat.Id, color: Color, t1: String): Option[Line] =
      Writer cut t1 flatMap { t2 =>
        flood.allowMessage(s"$chatId/${color.letter}", t2) option
          PlayerLine(color, Writer preprocessUserInput t2)
      }
  }

  private def publish(chatId: Chat.Id, msg: Any, busChan: BusChan.Select): Unit = {
    Bus.publish(msg, busChan(BusChan).chan)
    Bus.publish(msg, Chat chanOf chatId)
  }

  def remove(chatId: Chat.Id) = coll.delete.one($id(chatId)).void

  def removeAll(chatIds: List[Chat.Id]) = coll.delete.one($inIds(chatIds)).void

  private def pushLine(chatId: Chat.Id, line: Line): Funit =
    coll.update
      .one(
        $id(chatId),
        $doc(
          "$push" -> $doc(
            Chat.BSONFields.lines -> $doc(
              "$each"  -> List(line),
              "$slice" -> -maxLinesPerChat.value
            )
          )
        ),
        upsert = true
      )
      .void

  private object Writer {

    import java.util.regex.{ Matcher, Pattern }

    def preprocessUserInput(in: String) = multiline(spam.replace(noShouting(noPrivateUrl(in))))

    def cut(text: String) = Some(text.trim take Line.textMaxSize) filter (_.nonEmpty)

    private val gameUrlRegex                      = (Pattern.quote(netDomain.value) + """\b/(\w{8})\w{4}\b""").r
    private val gameUrlReplace                    = Matcher.quoteReplacement(netDomain.value) + "/$1";
    private def noPrivateUrl(str: String): String = gameUrlRegex.replaceAllIn(str, gameUrlReplace)
    private val multilineRegex                    = """\n\n{2,}+""".r
    private def multiline(str: String)            = multilineRegex.replaceAllIn(str, """\n\n""")
  }
}
