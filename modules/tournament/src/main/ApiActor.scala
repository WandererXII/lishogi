package lishogi.tournament

import akka.actor._
import org.joda.time.DateTime

import lishogi.game.actorApi.FinishGame

final private[tournament] class ApiActor(
    api: TournamentApi,
    leaderboard: LeaderboardApi
) extends Actor {

  implicit def ec = context.dispatcher

  lishogi.common.Bus.subscribe(
    self,
    "finishGame",
    "adjustCheater",
    "adjustBooster",
    "playban",
    "teamKick"
  )

  def receive = {

    case FinishGame(game, _, _) => api finishGame game

    case lishogi.playban.SittingDetected(game, player) => api.sittingDetected(game, player)

    case lishogi.hub.actorApi.mod.MarkCheater(userId, true) =>
      leaderboard.getAndDeleteRecent(userId, DateTime.now minusDays 3) flatMap {
        api.ejectLame(userId, _)
      }

    case lishogi.hub.actorApi.mod.MarkBooster(userId) => api.ejectLame(userId, Nil)

    case lishogi.hub.actorApi.round.Berserk(gameId, userId) => api.berserk(gameId, userId)

    case lishogi.hub.actorApi.playban.Playban(userId, _) => api.pausePlaybanned(userId)

    case lishogi.hub.actorApi.team.KickFromTeam(teamId, userId) => api.kickFromTeam(teamId, userId)
  }
}
