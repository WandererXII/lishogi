package lila.timeline

import scala.concurrent.ExecutionContextExecutor

import akka.actor._
import org.joda.time.DateTime

import lila.common.config.Max
import lila.hub.actorApi.timeline.Atom
import lila.hub.actorApi.timeline.Propagate
import lila.hub.actorApi.timeline.ReloadTimelines
import lila.hub.actorApi.timeline.propagation._
import lila.user.User

final private[timeline] class Push(
    relationApi: lila.relation.RelationApi,
    entryApi: EntryApi,
    unsubApi: UnsubApi,
) extends Actor {

  implicit def ec: ExecutionContextExecutor = context.dispatcher

  def receive = { case Propagate(data, propagations) =>
    propagate(propagations) flatMap { users =>
      unsubApi.filterUnsub(data.channel, users)
    } foreach { users =>
      if (users.nonEmpty)
        makeEntry(users, data) >>-
          lila.common.Bus.publish(ReloadTimelines(users), "lobbySocket")
      lila.mon.timeline.notification.increment(users.size)
    }
  }

  private def propagate(propagations: List[Propagation]): Fu[List[User.ID]] =
    scala.concurrent.Future.traverse(propagations) {
      case Users(ids)    => fuccess(ids)
      case Followers(id) => relationApi.freshFollowersFromSecondary(id)
      case Mutuals(id)   => relationApi.fetchMutuals(id)
      case ExceptUser(_) => fuccess(Nil)
    } map { users =>
      val excluded = propagations.collect { case ExceptUser(id) => id }.toSet
      users.flatten.distinct.filterNot(excluded.contains)
    }

  private def makeEntry(users: List[User.ID], data: Atom): Fu[Entry] = {
    val entry = Entry.make(data)
    entryApi.findRecent(entry.typ, DateTime.now minusMinutes 60, Max(1000)) flatMap { entries =>
      if (entries.exists(_ similarTo entry))
        fufail[Entry]("[timeline] a similar entry already exists")
      else entryApi insert Entry.ForUsers(entry, users) inject entry
    }
  }
}
