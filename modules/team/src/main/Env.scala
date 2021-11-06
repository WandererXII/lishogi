package lishogi.team

import akka.actor._
import com.softwaremill.macwire._

import lishogi.common.config._
import lishogi.mod.ModlogApi
import lishogi.notify.NotifyApi
import lishogi.socket.Socket.{ GetVersion, SocketVersion }

@Module
final class Env(
    captcher: lishogi.hub.actors.Captcher,
    timeline: lishogi.hub.actors.Timeline,
    teamSearch: lishogi.hub.actors.TeamSearch,
    userRepo: lishogi.user.UserRepo,
    modLog: ModlogApi,
    notifyApi: NotifyApi,
    remoteSocketApi: lishogi.socket.RemoteSocket,
    chatApi: lishogi.chat.ChatApi,
    cacheApi: lishogi.memo.CacheApi,
    lightUserApi: lishogi.user.LightUserApi,
    db: lishogi.db.Db
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: ActorSystem,
    mode: play.api.Mode
) {

  lazy val teamRepo    = new TeamRepo(db(CollName("team")))
  lazy val memberRepo  = new MemberRepo(db(CollName("team_member")))
  lazy val requestRepo = new RequestRepo(db(CollName("team_request")))

  lazy val forms = wire[DataForm]

  lazy val memberStream = wire[TeamMemberStream]

  lazy val api = wire[TeamApi]

  lazy val paginator = wire[PaginatorBuilder]

  lazy val cli = wire[Cli]

  lazy val cached: Cached = wire[Cached]

  lazy val jsonView = wire[JsonView]

  private val teamSocket = wire[TeamSocket]

  def version(teamId: Team.ID) =
    teamSocket.rooms.ask[SocketVersion](teamId)(GetVersion)

  private lazy val notifier = wire[Notifier]

  lazy val getTeamName = new GetTeamName(cached.blockingTeamName)

  lishogi.common.Bus.subscribeFun("shadowban", "teamIsLeader", "teamJoinedBy", "teamIsLeaderOf") {
    case lishogi.hub.actorApi.mod.Shadowban(userId, true) => api deleteRequestsByUserId userId
    case lishogi.hub.actorApi.team.IsLeader(teamId, userId, promise) =>
      promise completeWith cached.isLeader(teamId, userId)
    case lishogi.hub.actorApi.team.IsLeaderOf(leaderId, memberId, promise) =>
      promise completeWith api.isLeaderOf(leaderId, memberId)
    case lishogi.hub.actorApi.team.TeamIdsJoinedBy(userId, promise) =>
      promise completeWith cached.teamIdsList(userId)
  }
}
