package lishogi.round

import scala.concurrent.duration._

import lishogi.user.User
import lishogi.common.Bus

final class PlayingUsers {

  private val playing = new lishogi.memo.ExpireSetMemo(4 hours)

  def apply(userId: User.ID): Boolean = playing get userId

  Bus.subscribeFun("startGame", "finishGame") {

    case lishogi.game.actorApi.FinishGame(game, _, _) if game.hasClock =>
      game.userIds.some.filter(_.nonEmpty) foreach playing.removeAll

    case lishogi.game.actorApi.StartGame(game) if game.hasClock =>
      game.userIds.some.filter(_.nonEmpty) foreach playing.putAll
  }
}
