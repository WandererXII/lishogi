package lishogi.timeline

import akka.actor._
import org.joda.time.DateTime

import lishogi.common.config.Max
import lishogi.hub.actorApi.timeline.propagation._
import lishogi.hub.actorApi.timeline.{ Atom, Propagate, ReloadTimelines }
import lishogi.security.Permission
import lishogi.user.{ User, UserRepo }

final private[timeline] class Push(
    relationApi: lishogi.relation.RelationApi,
    userRepo: UserRepo,
    entryApi: EntryApi,
    unsubApi: UnsubApi
) extends Actor {

  implicit def ec = context.dispatcher

  def receive = { case Propagate(data, propagations) =>
    propagate(propagations) flatMap { users =>
      unsubApi.filterUnsub(data.channel, users)
    } foreach { users =>
      if (users.nonEmpty)
        makeEntry(users, data) >>-
          lishogi.common.Bus.publish(ReloadTimelines(users), "lobbySocket")
      lishogi.mon.timeline.notification.increment(users.size)
    }
  }

  private def propagate(propagations: List[Propagation]): Fu[List[User.ID]] =
    scala.concurrent.Future.traverse(propagations) {
      case Users(ids)    => fuccess(ids)
      case Followers(id) => relationApi.freshFollowersFromSecondary(id)
      case Friends(id)   => relationApi.fetchFriends(id)
      case ExceptUser(_) => fuccess(Nil)
      case ModsOnly(_)   => fuccess(Nil)
    } flatMap { users =>
      propagations.foldLeft(fuccess(users.flatten.distinct)) {
        case (fus, ExceptUser(id)) => fus.dmap(_.filter(id !=))
        case (fus, ModsOnly(true)) =>
          fus flatMap { us =>
            userRepo.userIdsWithRoles(modPermissions.map(_.dbKey)) dmap { userIds =>
              us filter userIds.contains
            }
          }
        case (fus, _) => fus
      }
    }

  private def modPermissions =
    List(
      Permission.ModNote,
      Permission.Doxing,
      Permission.Admin,
      Permission.SuperAdmin
    )

  private def makeEntry(users: List[User.ID], data: Atom): Fu[Entry] = {
    val entry = Entry.make(data)
    entryApi.findRecent(entry.typ, DateTime.now minusMinutes 60, Max(1000)) flatMap { entries =>
      if (entries.exists(_ similarTo entry)) fufail[Entry]("[timeline] a similar entry already exists")
      else entryApi insert Entry.ForUsers(entry, users) inject entry
    }
  }
}
