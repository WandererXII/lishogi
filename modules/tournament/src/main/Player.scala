package lila.tournament

import lila.common.LightUser
import lila.hub.LightTeam.TeamID
import lila.rating.PerfType
import lila.user.User

private[tournament] case class Player(
    _id: Player.ID, // random
    tourId: Tournament.ID,
    userId: User.ID,
    order: Option[Int], // only for tours with arrangements
    rating: Int,
    provisional: Boolean,
    withdraw: Boolean = false,
    kicked: Boolean = false,
    score: Int = 0,
    fire: Boolean = false,
    performance: Int = 0,
    team: Option[TeamID] = None,
) {

  def id = _id

  def active = !withdraw

  def is(uid: User.ID): Boolean  = uid == userId
  def is(user: User): Boolean    = is(user.id)
  def is(other: Player): Boolean = is(other.userId)

  def magicScore = (score * 10000 + (order | (performanceOption | rating))) * (if (kicked) 0 else 1)
  def scoreNotKicked = if (kicked) 0 else score

  def performanceOption = performance > 0 option performance
}

private[tournament] object Player {

  type ID = String

  case class WithUser(player: Player, user: User)

  case class Result(player: Player, lightUser: LightUser, rank: Int)

  private[tournament] def make(
      tourId: Tournament.ID,
      user: User,
      perfType: PerfType,
      team: Option[TeamID],
      order: Option[Int],
  ): Player =
    Player(
      _id = lila.common.ThreadLocalRandom.nextString(8),
      tourId = tourId,
      userId = user.id,
      order = order,
      rating = user.perfs(perfType).intRating,
      provisional = user.perfs(perfType).provisional,
      team = team,
    )
}
