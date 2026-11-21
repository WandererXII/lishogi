package lila.plan

import scala.concurrent.duration._

import org.joda.time.DateTime
import reactivemongo.api._

import lila.common.Bus
import lila.common.config.Secret
import lila.db.dsl._
import lila.memo.CacheApi._
import lila.user.User
import lila.user.UserRepo

final class PlanApi(
    patronColl: Coll,
    chargeColl: Coll,
    notifier: PlanNotifier,
    userRepo: UserRepo,
    lightUserApi: lila.user.LightUserApi,
    cacheApi: lila.memo.CacheApi,
    mongoCache: lila.memo.MongoCache.Api,
    payPalIpnKey: Secret,
    monthlyGoalApi: MonthlyGoalApi,
)(implicit ec: scala.concurrent.ExecutionContext) {

  import BsonHandlers._
  import ChargeHandlers._
  import PatronHandlers._

  def onPaypalCharge(
      userId: Option[User.ID],
      email: Option[Patron.PayPal.Email],
      subId: Option[Patron.PayPal.SubId],
      cents: Cents,
      name: Option[String],
      txnId: Option[String],
      ip: String,
      key: String,
  ): Funit =
    if (key != payPalIpnKey.value) {
      logger.error(s"Invalid PayPal IPN key $key from $ip $userId $cents")
      funit
    } else if (cents.value < 100) {
      logger.info(s"Ignoring small paypal charge from $ip $userId $cents $txnId")
      funit
    } else {
      val charge = Charge.make(
        userId = userId,
        payPal = Charge
          .PayPal(
            name = name,
            email = email.map(_.value),
            txnId = txnId,
            subId = subId.map(_.value),
            ip = ip.some,
          )
          .some,
        cents = cents,
      )
      addCharge(charge) >>
        (userId ?? userRepo.named) flatMap { userOption =>
          userOption ?? { user =>
            val payPal = Patron.PayPal(email, subId, DateTime.now)
            userPatron(user).flatMap {
              case None =>
                patronColl.insert.one(
                  Patron(
                    _id = Patron.UserId(user.id),
                    payPal = payPal.some,
                    lastLevelUp = Some(DateTime.now),
                  ).expireInOneMonth,
                ) >>
                  setDbUserPlan(user, lila.user.Plan.start) >>
                  notifier.onStart(user)
              case Some(patron) =>
                val p2 = patron
                  .copy(
                    payPal = payPal.some,
                    free = none,
                  )
                  .levelUpIfPossible
                  .expireInOneMonth
                patronColl.update.one($id(patron.id), p2) >>
                  setDbUserPlanOnCharge(user, p2)
            } >> {
              charge.lifetimeWorthy ?? setLifetime(user)
            } >>- logger.info(s"Charged ${user.username} with paypal: $cents")
          }
        }
    }

  private def setDbUserPlanOnCharge(user: User, patron: Patron): Funit = {
    val plan =
      if (patron.canLevelUp) user.plan.incMonths
      else user.plan.enable
    Bus.publish(lila.hub.actorApi.plan.MonthInc(user.id, plan.months), "plan")
    setDbUserPlan(user, plan)
  }

  import PlanApi.SyncResult.ReloadUser
  import PlanApi.SyncResult.Synced

  def sync(user: User): Fu[PlanApi.SyncResult] =
    userPatron(user) flatMap {

      case None if user.plan.active =>
        logger.warn(s"${user.username} sync: disable plan of non-patron")
        setDbUserPlan(user, user.plan.disable) inject ReloadUser

      case None => fuccess(Synced(none))

      case Some(patron) =>
        patron.payPal match {

          case Some(_) =>
            if (!user.plan.active) {
              logger.warn(s"${user.username} sync: enable plan of customer with paypal")
              setDbUserPlan(user, user.plan.enable) inject ReloadUser
            } else fuccess(Synced(patron.some))

          case None if patron.isLifetime => fuccess(Synced(patron.some))

          case None if patron.isPatreon => fuccess(Synced(patron.some))

          case None if user.plan.active && patron.free.isEmpty =>
            logger.warn(s"${user.username} sync: disable plan of patron with no paypal")
            setDbUserPlan(user, user.plan.disable) inject ReloadUser

          case _ => fuccess(Synced(patron.some))
        }
    }

  def isLifetime(user: User): Fu[Boolean] =
    userPatron(user) map {
      _.exists(_.isLifetime)
    }

  def isPatreon(user: User): Fu[Boolean] =
    userPatron(user) map {
      _.exists(_.isPatreon)
    }

  def unsetPatreon(user: User): Funit =
    userPatron(user) flatMap { patronOpt =>
      val patron = patronOpt
        .getOrElse(Patron(_id = Patron.UserId(user.id)))
        .copy(
          patreon = false.some,
        )
        .expireInOneMonth
      patronColl.update
        .one(
          $id(user.id),
          patron,
        )
        .void >>- lightUserApi.invalidate(user.id)
    }

  def setPatreon(user: User, amount: Int): Funit = {
    val charge = Charge.make(
      userId = Some(user.id),
      cents = Cents(amount * 100), // dollars to cents
    )
    addCharge(charge)
    userRepo.setPlan(
      user,
      lila.user.Plan(
        months = user.plan.months | 1,
        active = true,
        since = user.plan.since orElse DateTime.now.some,
      ),
    ) >> patronColl.update
      .one(
        $id(user.id),
        $set(
          "lastLevelUp" -> DateTime.now,
          "lifetime"    -> false,
          "patreon"     -> true,
          "expiresAt"   -> DateTime.now.plusMonths(1).plusDays(1),
        ),
        upsert = true,
      )
      .void >>- lightUserApi.invalidate(user.id)
  }

  def setLifetime(user: User): Funit =
    userRepo.setPlan(
      user,
      lila.user.Plan(
        months = user.plan.months | 1,
        active = true,
        since = user.plan.since orElse DateTime.now.some,
      ),
    ) >> patronColl.update
      .one(
        $id(user.id),
        $set(
          "lastLevelUp" -> DateTime.now,
          "lifetime"    -> true,
          "free"        -> Patron.Free(DateTime.now),
        ),
        upsert = true,
      )
      .void >>- lightUserApi.invalidate(user.id)

  def giveMonth(user: User): Funit =
    userRepo.setPlan(
      user,
      lila.user.Plan(
        months = user.plan.months | 1,
        active = true,
        since = user.plan.since orElse DateTime.now.some,
      ),
    ) >> patronColl.update
      .one(
        $id(user.id),
        $set(
          "lastLevelUp" -> DateTime.now,
          "lifetime"    -> false,
          "free"        -> Patron.Free(DateTime.now),
          "expiresAt"   -> DateTime.now.plusMonths(1).plusDays(1),
        ),
        upsert = true,
      )
      .void >>- lightUserApi.invalidate(user.id)

  private val recentChargeUserIdsNb = 100
  private val recentChargeUserIdsCache = cacheApi.unit[List[User.ID]] {
    _.refreshAfterWrite(30 minutes)
      .buildAsyncFuture { _ =>
        chargeColl.primitive[User.ID](
          $empty,
          sort = $doc("date" -> -1),
          nb = recentChargeUserIdsNb * 3 / 2,
          "userId",
        ) flatMap filterUserIds dmap (_ take recentChargeUserIdsNb)
      }
  }

  def recentChargeUserIds: Fu[List[User.ID]] = recentChargeUserIdsCache.getUnit

  def recentChargesOf(user: User): Fu[List[Charge]] =
    chargeColl.find($doc("userId" -> user.id)).sort($doc("date" -> -1)).cursor[Charge]().list()

  private val topPatronUserIdsNb = 300
  private val topPatronUserIdsCache = mongoCache.unit[List[User.ID]](
    "patron:top",
    59 minutes,
  ) { loader =>
    _.refreshAfterWrite(60 minutes)
      .buildAsyncFuture {
        loader { _ =>
          chargeColl
            .aggregateList(
              maxDocs = topPatronUserIdsNb * 2,
              readPreference = ReadPreference.secondaryPreferred,
            ) { framework =>
              import framework._
              Match($doc("userId" $exists true)) -> List(
                GroupField("userId")("total" -> SumField("cents")),
                Sort(Descending("total")),
                Limit(topPatronUserIdsNb * 3 / 2),
              )
            }
            .dmap {
              _.flatMap { _.getAsOpt[User.ID]("_id") }
            } flatMap filterUserIds dmap (_ take topPatronUserIdsNb)
        }
      }
  }

  def topPatronUserIds: Fu[List[User.ID]] = topPatronUserIdsCache.get {}

  private def filterUserIds(ids: List[User.ID]): Fu[List[User.ID]] = {
    val dedup = ids.distinct
    userRepo.filterByEnabledPatrons(dedup) map { enableds =>
      dedup filter enableds.contains
    }
  }

  private def addCharge(charge: Charge): Funit =
    chargeColl.insert.one(charge).void >>- {
      recentChargeUserIdsCache.invalidateUnit()
      monthlyGoalApi.get foreach { m =>
        Bus.publish(
          lila.hub.actorApi.plan.ChargeEvent(
            username = charge.userId.flatMap(lightUserApi.sync).fold("Anonymous")(_.name),
            amount = charge.cents.value,
            percent = m.percent,
            DateTime.now,
          ),
          "plan",
        )
        lila.mon.plan.goal.update(m.goal.value)
        lila.mon.plan.current.update(m.current.value)
        lila.mon.plan.percent.update(m.percent)
        if (charge.isPayPal) lila.mon.plan.paypal.record(charge.cents.value)
      }
    }

  private def setDbUserPlan(user: User, plan: lila.user.Plan): Funit =
    userRepo.setPlan(user, plan) >>- lightUserApi.invalidate(user.id)

  def userPatron(user: User): Fu[Option[Patron]] = patronColl.one[Patron]($id(user.id))

}

object PlanApi {

  sealed trait SyncResult
  object SyncResult {
    case object ReloadUser                    extends SyncResult
    case class Synced(patron: Option[Patron]) extends SyncResult
  }
}
