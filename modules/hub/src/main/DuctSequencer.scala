package lila.hub

import scala.concurrent.ExecutionContext
import scala.concurrent.Promise
import scala.concurrent.duration.FiniteDuration

import com.github.blemale.scaffeine.LoadingCache

final class DuctSequencer(
    maxSize: Int,
    timeout: FiniteDuration,
    name: String,
    logging: Boolean = true,
)(implicit
    system: akka.actor.ActorSystem,
    ec: ExecutionContext,
) {

  import DuctSequencer._

  def apply[A](fu: => Fu[A]): Fu[A] = run(() => fu)

  def run[A](task: Task[A]): Fu[A] = duct.ask[A](TaskWithPromise(task, _))

  private[this] val duct =
    new BoundedDuct(maxSize, name, logging)({ case TaskWithPromise(task, promise) =>
      promise.completeWith {
        task().withTimeout(timeout)
      }.future
    })
}

// Distributes tasks to many sequencers
final class DuctSequencers(
    maxSize: Int,
    expiration: FiniteDuration,
    timeout: FiniteDuration,
    name: String,
    logging: Boolean = true,
)(implicit
    system: akka.actor.ActorSystem,
    ec: ExecutionContext,
) {

  def apply[A](key: String)(task: => Fu[A]): Fu[A] =
    sequencers.get(key).run(() => task)

  private val sequencers: LoadingCache[String, DuctSequencer] =
    lila.common.LilaCache.scaffeine
      .expireAfterAccess(expiration)
      .build(key => new DuctSequencer(maxSize, timeout, s"$name:$key", logging))
}

object DuctSequencer {

  private type Task[A] = () => Fu[A]
  private case class TaskWithPromise[A](task: Task[A], promise: Promise[A])
}
