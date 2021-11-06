package lishogi.activity

import akka.actor._
import com.softwaremill.macwire._
import scala.concurrent.duration._

import lishogi.common.config._
import lishogi.hub.actorApi.round.CorresMoveEvent

final class Env(
    db: lishogi.db.Db,
    practiceApi: lishogi.practice.PracticeApi,
    gameRepo: lishogi.game.GameRepo,
    postApi: lishogi.forum.PostApi,
    simulApi: lishogi.simul.SimulApi,
    studyApi: lishogi.study.StudyApi,
    tourLeaderApi: lishogi.tournament.LeaderboardApi,
    getTourName: lishogi.tournament.GetTourName,
    getTeamName: lishogi.team.GetTeamName
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: ActorSystem
) {

  private lazy val coll = db(CollName("activity"))

  lazy val write: ActivityWriteApi = wire[ActivityWriteApi]

  lazy val read: ActivityReadApi = wire[ActivityReadApi]

  lazy val jsonView = wire[JsonView]

  lishogi.common.Bus.subscribeFun(
    "finishGame",
    "forumPost",
    "finishPuzzle",
    "finishPractice",
    "team",
    "startSimul",
    "moveEventCorres",
    "plan",
    "relation",
    "startStudy",
    "streamStart",
    "stormRun"
  ) {
    case lishogi.game.actorApi.FinishGame(game, _, _) if !game.aborted => write game game
    case lishogi.forum.actorApi.CreatePost(post)                       => write.forumPost(post)
    case res: lishogi.puzzle.Puzzle.UserResult                         => write puzzle res
    case prog: lishogi.practice.PracticeProgress.OnComplete            => write practice prog
    case lishogi.simul.Simul.OnStart(simul)                            => write simul simul
    case CorresMoveEvent(move, Some(userId), _, _, false)           => write.corresMove(move.gameId, userId)
    case lishogi.hub.actorApi.plan.MonthInc(userId, months)            => write.plan(userId, months)
    case lishogi.hub.actorApi.relation.Follow(from, to)                => write.follow(from, to)
    case lishogi.study.actorApi.StartStudy(id)                         =>
      // wait some time in case the study turns private
      system.scheduler.scheduleOnce(5 minutes) { write study id }
    case lishogi.hub.actorApi.storm.StormRun(userId, score)  => write.storm(userId, score)
    case lishogi.hub.actorApi.team.CreateTeam(id, _, userId) => write.team(id, userId)
    case lishogi.hub.actorApi.team.JoinTeam(id, userId)      => write.team(id, userId)
    case lishogi.hub.actorApi.streamer.StreamStart(userId)   => write.streamStart(userId)
    case lishogi.user.User.GDPRErase(user)                   => write erase user
  }
}
