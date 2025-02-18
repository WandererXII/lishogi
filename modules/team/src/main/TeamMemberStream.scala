package lila.team

import scala.concurrent.duration._

import akka.stream.scaladsl._
import reactivemongo.akkastream.cursorProducer
import reactivemongo.api.ReadPreference

import lila.common.config.MaxPerSecond
import lila.db.dsl._
import lila.user.User
import lila.user.UserRepo

final class TeamMemberStream(
    memberRepo: MemberRepo,
    userRepo: UserRepo,
)(implicit
    mat: akka.stream.Materializer,
) {

  def apply(team: Team, perSecond: MaxPerSecond): Source[User, _] =
    idsBatches(team, perSecond)
      .mapAsync(1)(userRepo.usersFromSecondary)
      .mapConcat(identity)

  def subscribedIds(team: Team, perSecond: MaxPerSecond): Source[User.ID, _] =
    idsBatches(team, perSecond, $doc("unsub" $ne true))
      .mapConcat(identity)

  private def idsBatches(
      team: Team,
      perSecond: MaxPerSecond,
      selector: Bdoc = $empty,
  ): Source[Seq[User.ID], _] =
    memberRepo.coll
      .find($doc("team" -> team.id) ++ selector, $doc("user" -> true).some)
      .sort($sort desc "date")
      .batchSize(perSecond.value)
      .cursor[Bdoc](ReadPreference.secondaryPreferred)
      .documentSource()
      .grouped(perSecond.value)
      .map(_.flatMap(_.getAsOpt[User.ID]("user")))
      .throttle(1, 1 second)
}
