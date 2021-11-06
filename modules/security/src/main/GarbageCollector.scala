package lishogi.security

import org.joda.time.DateTime
import play.api.mvc.RequestHeader
import scala.concurrent.duration._

import lishogi.common.{ Bus, EmailAddress, HTTPRequest, IpAddress, ThreadLocalRandom }
import lishogi.user.User

// codename UGC
final class GarbageCollector(
    userSpy: UserSpyApi,
    ipTrust: IpTrust,
    slack: lishogi.slack.SlackApi,
    noteApi: lishogi.user.NoteApi,
    isArmed: () => Boolean
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: akka.actor.ActorSystem
) {

  private val logger = lishogi.security.logger.branch("GarbageCollector")

  private val done = new lishogi.memo.ExpireSetMemo(10 minutes)

  private case class ApplyData(user: User, ip: IpAddress, email: EmailAddress, req: RequestHeader) {
    override def toString = s"${user.username} $ip ${email.value} $req"
  }

  // User just signed up and doesn't have security data yet, so wait a bit
  def delay(user: User, email: EmailAddress, req: RequestHeader): Unit =
    if (user.createdAt.isAfter(DateTime.now minusDays 3)) {
      val ip = HTTPRequest lastRemoteAddress req
      system.scheduler.scheduleOnce(6 seconds) {
        val applyData = ApplyData(user, ip, email, req)
        logger.debug(s"delay $applyData")
        lishogi.common.Future
          .retry(
            () => ensurePrintAvailable(applyData),
            delay = 10 seconds,
            retries = 5,
            logger = none
          )
          .nevermind >> apply(applyData)
      }
    }

  private def ensurePrintAvailable(data: ApplyData): Funit =
    userSpy userHasPrint data.user flatMap {
      case false => fufail("No print available yet")
      case _     => funit
    }

  private def apply(data: ApplyData): Funit =
    data match {
      case ApplyData(user, ip, email, req) =>
        for {
          spy    <- userSpy(user, 300)
          ipSusp <- ipTrust.isSuspicious(ip)
        } yield {
          val printOpt = spy.prints.headOption
          logger.debug(s"apply ${data.user.username} print=${printOpt}")
          Bus.publish(
            lishogi.security.UserSignup(user, email, req, printOpt.map(_.fp.value), ipSusp),
            "userSignup"
          )
          printOpt.filter(_.banned).map(_.fp.value) match {
            case Some(print) => collect(user, email, msg = s"Print ban: ${print.value}")
            case _ =>
              badOtherAccounts(spy.otherUsers.map(_.user)) ?? { others =>
                logger.debug(s"other ${data.user.username} others=${others.map(_.username)}")
                lishogi.common.Future
                  .exists(spy.ips)(ipTrust.isSuspicious)
                  .map {
                    _ ?? collect(
                      user,
                      email,
                      msg = s"Prev users: ${others.map(o => "@" + o.username).mkString(", ")}"
                    )
                  }
              }
          }
        }
    }

  private def badOtherAccounts(accounts: List[User]): Option[List[User]] = {
    val others = accounts
      .sortBy(-_.createdAt.getSeconds)
      .takeWhile(_.createdAt.isAfter(DateTime.now minusDays 10))
      .take(4)
    (others.size > 1 && others.forall(isBadAccount) && others.headOption.exists(_.disabled)) option others
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
      slack.garbageCollector(message) >>- {
        if (armed) {
          doInitialSb(user)
          system.scheduler.scheduleOnce(wait) {
            doCollect(user)
          }
        }
      }
    }

  private def doInitialSb(user: User): Unit =
    Bus.publish(
      lishogi.hub.actorApi.security.GCImmediateSb(user.id),
      "garbageCollect"
    )

  private def doCollect(user: User): Unit =
    Bus.publish(
      lishogi.hub.actorApi.security.GarbageCollect(user.id),
      "garbageCollect"
    )
}
