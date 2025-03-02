package lila.round

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

import lila.chat.Chat
import lila.chat.ChatApi
import lila.chat.ChatTimeout
import lila.game.Game
import lila.hub.actorApi.shutup.PublicSource
import lila.i18n.I18nKey
import lila.user.User

final class Messenger(api: ChatApi) {

  private val dateTimeFormatter = DateTimeFormat forStyle "MS"
  private def timestampMessage(str: String, stepNumber: Int): String =
    s"[${dateTimeFormatter print DateTime.now} ($stepNumber. move)]${str.toLowerCase.capitalize}"

  def systemWithTimestamp(game: Game, trans: I18nKey, args: String*): Unit =
    system(true)(game, timestampMessage(s"key:${trans.key}:${args.mkString(",")}", game.plies))

  def system(game: Game, trans: I18nKey, args: String*): Unit =
    system(true)(game, s"key:${trans.key}:${args.mkString(",")}")

  def system(game: Game, message: String): Unit =
    system(true)(game, message)

  def volatile(game: Game, message: String): Unit =
    system(false)(game, message)

  private def system(persistent: Boolean)(game: Game, message: String): Unit = {
    val apiCall =
      if (persistent) api.userChat.system _
      else api.userChat.volatile _
    apiCall(watcherId(Chat.Id(game.id)), message, _.Round)
    if (game.nonAi) apiCall(Chat.Id(game.id), message, _.Round)
  }.unit

  def systemForOwners(chatId: Chat.Id, message: String): Unit =
    api.userChat.system(chatId, message, _.Round).unit

  def watcher(gameId: Game.Id, userId: User.ID, text: String) =
    api.userChat.write(
      watcherId(gameId),
      userId,
      text,
      PublicSource.Watcher(gameId.value).some,
      _.Round,
    )

  private val whisperCommands = List("/whisper ", "/w ")

  def owner(gameId: Game.Id, userId: User.ID, text: String): Funit =
    whisperCommands.collectFirst {
      case command if text startsWith command =>
        val source = PublicSource.Watcher(gameId.value)
        api.userChat.write(watcherId(gameId), userId, text drop command.size, source.some, _.Round)
    } getOrElse {
      (!text.startsWith("/")) ?? // mistyped command?
        api.userChat.write(Chat.Id(gameId.value), userId, text, publicSource = none, _.Round)
    }

  def owner(gameId: Game.Id, anonColor: shogi.Color, text: String): Funit =
    api.playerChat.write(Chat.Id(gameId.value), anonColor, text, _.Round)

  def timeout(
      chatId: Chat.Id,
      modId: User.ID,
      suspect: User.ID,
      reason: String,
      text: String,
  ): Funit =
    ChatTimeout.Reason(reason) ?? { r =>
      api.userChat.timeout(chatId, modId, suspect, r, ChatTimeout.Scope.Global, text, _.Round)
    }

  private def watcherId(chatId: Chat.Id) = Chat.Id(s"$chatId/w")
  private def watcherId(gameId: Game.Id) = Chat.Id(s"$gameId/w")
}
