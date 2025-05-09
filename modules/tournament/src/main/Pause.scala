package lila.tournament

import scala.concurrent.duration._

import org.joda.time.DateTime

import lila.user.User

/*
 * Computes the delay before a player can rejoin a tournament after pausing.
 * The first pause results in a delay of 10 seconds.
 * Next delays durations increase linearly as `pauses * gameTotalTime / 15`
 * (e.g. 20 seconds for second pause in 5+0) with maximum of 120 seconds.
 * After 20 minutes without any pause, the delay is reinitialized to 10s.
 */
final private class Pause {

  import Pause._

  private val cache = lila.memo.CacheApi.scaffeineNoScheduler
    .expireAfterWrite(20 minutes)
    .build[User.ID, Record]()

  private val minDelay = 10
  private val maxDelay = 120

  private def baseDelayOf(tour: Tournament) =
    Delay {
      (tour.timeControl.estimateTotalSeconds / 15)
    }

  private def delayOf(record: Record, tour: Tournament) =
    Delay {
      // 10s for first pause
      // next ones increasing linearly until 120s
      baseDelayOf(tour).seconds * (record.pauses - 1) atLeast minDelay atMost maxDelay
    }

  def add(userId: User.ID): Unit =
    cache.put(
      userId,
      cache.getIfPresent(userId).fold(newRecord)(_.add),
    )

  def remainingDelay(userId: User.ID, tour: Tournament): Option[Delay] =
    cache getIfPresent userId flatMap { record =>
      val seconds = record.pausedAt.getSeconds - nowSeconds + delayOf(record, tour).seconds
      seconds > 1 option Delay(seconds.toInt)
    }

  def canJoin(userId: User.ID, tour: Tournament): Boolean =
    remainingDelay(userId, tour).isEmpty
}

object Pause {

  case class Record(pauses: Int, pausedAt: DateTime) {
    def add =
      copy(
        pauses = pauses + 1,
        pausedAt = DateTime.now,
      )
  }
  val newRecord = Record(1, DateTime.now)

  // pause counter of a player
  case class Delay(seconds: Int) extends AnyVal
}
