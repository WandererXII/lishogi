package lishogi.round

import scala.concurrent.duration._

import lishogi.game.{ Game, GameRepo }

final class RecentTvGames(
    gameRepo: GameRepo
) {
  private val fast = new lishogi.memo.ExpireSetMemo(7 minutes)
  private val slow = new lishogi.memo.ExpireSetMemo(2 hours)

  def get(gameId: Game.ID) = fast.get(gameId) || slow.get(gameId)

  def put(game: Game) = {
    gameRepo.setTv(game.id)
    (if (game.speed <= shogi.Speed.Bullet) fast else slow) put game.id
  }
}
