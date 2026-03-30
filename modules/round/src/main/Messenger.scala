package lila.round

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

import lila.chat.Chat
import lila.chat.ChatApi
import lila.chat.ChatTimeout
import lila.game.Game
import lila.i18n.I18nKey
import lila.user.User

final class Messenger(api: ChatApi) {

  private val dateTimeFormatter = DateTimeFormat forStyle "MS"
  private def timestampMessage(str: String, stepNumber: Int): String =
    s"[${dateTimeFormatter print DateTime.now} ($stepNumber. move)]$str"

  private def colorAndHandicapFormat(colorAndHandicap: Option[(shogi.Color, Boolean)]): String =
    colorAndHandicap ?? { ch => s"${ch._1.toString}:${ch._2}" }

  def systemWithTimestamp(
      game: Game,
      trans: I18nKey,
      colorAndHandicap: Option[(shogi.Color, Boolean)] = none,
      volatile: Boolean = false,
  ): Unit =
    system(
      game,
      timestampMessage(s"key:${trans.key}:${colorAndHandicapFormat(colorAndHandicap)}", game.plies),
      volatile,
    )

  def systemTrans(
      game: Game,
      trans: I18nKey,
      colorAndHandicap: Option[(shogi.Color, Boolean)] = none,
      volatile: Boolean = false,
  ): Unit =
    system(game, s"key:${trans.key}:${colorAndHandicapFormat(colorAndHandicap)}", volatile)

  def system(game: Game, message: String, volatile: Boolean = false): Unit = {
    api.system(Chat.Id(game.id), message, _.Round, volatile)
  }.unit

  def user(gameId: Game.Id, userId: User.ID, text: String): Funit =
    api.write(Chat.Id(gameId.value), userId, text, publicSource = none, _.Round)

  def anon(gameId: Game.Id, anonColor: shogi.Color, text: String): Funit =
    api.writeAnon(Chat.Id(gameId.value), anonColor, text, _.Round)

  def timeout(
      chatId: Chat.Id,
      modId: User.ID,
      suspect: User.ID,
      reason: String,
      text: String,
  ): Funit =
    ChatTimeout.Reason(reason) ?? { r =>
      api.timeout(chatId, modId, suspect, r, ChatTimeout.Scope.Global, text, _.Round)
    }

}
