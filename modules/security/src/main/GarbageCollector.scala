package lila.security

import scala.concurrent.duration._

import org.joda.time.DateTime

import lila.common.Bus
import lila.common.EmailAddress
import lila.common.ThreadLocalRandom
import lila.user.User

// codename UGC
final class GarbageCollector(
    userSpy: UserSpyApi,
    ipTrust: IpTrust,
    noteApi: lila.user.NoteApi,
    isArmed: () => Boolean,
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: akka.actor.ActorSystem,
) {

  private val logger = lila.security.logger.branch("GarbageCollector")

  private val done = new lila.memo.ExpireSetMemo(10 minutes)

  private case class ApplyData(user: User, email: EmailAddress) {
    override def toString = s"${user.username} ${email.value}"
  }

  // User just signed up and doesn't have security data yet, so wait a bit
  def delay(user: User, email: EmailAddress): Unit =
    if (user.createdAt.isAfter(DateTime.now minusDays 3)) {
      system.scheduler
        .scheduleOnce(6 seconds) {
          val applyData = ApplyData(user, email)
          logger.debug(s"delay $applyData")
          lila.common.Future
            .retry(
              () => ensurePrintAvailable(applyData),
              delay = 10 seconds,
              retries = 5,
              logger = none,
            )
            .nevermind >> apply(applyData)
          ()
        }
        .unit
    }

  private def ensurePrintAvailable(data: ApplyData): Funit =
    userSpy userHasPrint data.user flatMap {
      case false => fufail("No print available yet")
      case _     => funit
    }

  private def apply(data: ApplyData): Funit =
    data match {
      case ApplyData(user, email) =>
        for {
          spy <- userSpy(user, 300)
          _ <- {
            spy.prints.find(_.banned).map(_.fp.value) match {
              case Some(print) => collect(user, email, msg = s"Print ban: ${print.value}")
              case _ =>
                badOtherAccounts(spy.otherUsers.map(_.user)) ?? { others =>
                  logger.debug(s"other ${data.user.username} others=${others.map(_.username)}")
                  spy.ips.exists(ipTrust.isSuspicious) ?? collect(
                    user,
                    email,
                    msg = s"Prev users: ${others.map(o => "@" + o.username).mkString(", ")}",
                  )
                }
            }
          }
        } yield ()
    }

  private def badOtherAccounts(accounts: List[User]): Option[List[User]] = {
    val others = accounts
      .sortBy(-_.createdAt.getSeconds)
      .takeWhile(_.createdAt.isAfter(DateTime.now minusDays 10))
      .take(4)
    (others.sizeIs > 1 && others.forall(isBadAccount) && others.headOption.exists(
      _.disabled,
    )) option others
  }

  private def isBadAccount(user: User) = user.lameOrTrollOrAlt

  private def collect(user: User, email: EmailAddress, msg: => String): Funit =
    !done.get(user.id) ?? {
      done put user.id
      val armed = isArmed()
      val wait  = (30 + ThreadLocalRandom.nextInt(300)).seconds
      val message =
        s"Will dispose of @${user.username} in $wait. Email: ${email.value}. $msg${!armed ?? " [SIMULATION]"}"
      logger.info(message)
      noteApi.lishogiWrite(user, s"Garbage collected because of $msg")
      fuccess(
        if (armed) {
          doInitialSb(user)
          system.scheduler
            .scheduleOnce(wait) {
              doCollect(user)
            }
            .unit
        },
      )
    }

  private def doInitialSb(user: User): Unit =
    Bus.publish(
      lila.hub.actorApi.security.GCImmediateSb(user.id),
      "garbageCollect",
    )

  private def doCollect(user: User): Unit =
    Bus.publish(
      lila.hub.actorApi.security.GarbageCollect(user.id),
      "garbageCollect",
    )
}
