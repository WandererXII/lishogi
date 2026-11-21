package lila.shoginet

import scala.concurrent.duration._

final private class Monitor(
    repo: ShoginetRepo,
    @scala.annotation.unused moveDb: MoveDB,
    cacheApi: lila.memo.CacheApi,
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: akka.actor.ActorSystem,
) {

  val statusCache = cacheApi.unit[Monitor.Status] {
    _.refreshAfterWrite(2 minute)
      .buildAsyncFuture { _ =>
        repo.status.compute
      }
  }

  private val monBy = lila.mon.shoginet.analysis.by

  private def sumOf[A](items: List[A])(f: A => Option[Int]) =
    items.foldLeft(0) { case (acc, a) =>
      acc + f(a).getOrElse(0)
    }

  private[shoginet] def analysis(
      work: Work.Analysis,
      client: Client,
      result: JsonApi.Request.CompleteAnalysis,
  ) = {
    Monitor.success(work, client)

    val userId = client.userId.value

    monBy.totalSecond(userId).increment(sumOf(result.evaluations)(_.time) / 1000)
    monBy
      .totalMeganode(userId)
      .increment(sumOf(result.evaluations) { eval =>
        eval.nodes ifFalse eval.mateFound
      } / 1000000)

    val metaMovesSample = sample(result.evaluations.drop(6).filterNot(_.mateFound), 100)
    def avgOf(f: JsonApi.Request.Evaluation => Option[Int]): Option[Int] = {
      val (sum, nb) = metaMovesSample.foldLeft(0 -> 0) { case ((sum, nb), move) =>
        f(move).fold(sum -> nb) { v =>
          (sum + v, nb + 1)
        }
      }
      (nb > 0) option (sum / nb)
    }
    avgOf(_.time) foreach { monBy.movetime(userId).record(_) }
    avgOf(_.nodes) foreach { monBy.node(userId).record(_) }
    avgOf(_.cappedNps) foreach { monBy.nps(userId).record(_) }
    avgOf(_.depth) foreach { monBy.depth(userId).record(_) }
    avgOf(_.pv.size.some) foreach { monBy.pvSize(userId).record(_) }

    val significantPvSizes =
      result.evaluations.filterNot(_.mateFound).filterNot(_.deadDraw).map(_.pv.size)

    monBy.pv(userId, isLong = false).increment(significantPvSizes.count(_ < 3))
    monBy.pv(userId, isLong = true).increment(significantPvSizes.count(_ >= 6))
  }

  private def sample[A](elems: List[A], n: Int) =
    if (elems.sizeIs <= n) elems else lila.common.ThreadLocalRandom shuffle elems take n

  private def monitorClients(): Funit =
    repo.allRecentClients map { clients =>
      import lila.mon.shoginet.client._

      status(true).update(clients.count(_.enabled))
      status(false).update(clients.count(_.disabled))

      val instances = clients.flatMap(_.instance)

      instances.map(_.version.value).groupBy(identity).view.mapValues(_.size) foreach {
        case (v, nb) =>
          version(v).update(nb)
      }
    }

  private def monitorStatus(): Funit =
    statusCache.get {} map { c =>
      lila.mon.shoginet.work("queued", "system").update(c.system.queued)
      lila.mon.shoginet.work("queued", "user").update(c.user.queued)
      lila.mon.shoginet.work("acquired", "system").update(c.system.acquired)
      lila.mon.shoginet.work("acquired", "user").update(c.user.acquired)
      lila.mon.shoginet.work("puzzles", "queued").update(c.puzzles.queued)
      lila.mon.shoginet.work("puzzles", "acquired").update(c.puzzles.acquired)
      ()
    }

  system.scheduler.scheduleWithFixedDelay(1 minute, 1 minute) { () =>
    monitorClients() >> monitorStatus()
    ()
  }
}

object Monitor {

  case class StatusFor(acquired: Int, queued: Int)

  case class Status(user: StatusFor, system: StatusFor, puzzles: StatusFor)

  private val monResult = lila.mon.shoginet.client.result

  private[shoginet] def move(client: Client) = {
    monResult.success(client.userId.value).increment()
  }

  private def success(work: Work.Analysis, client: Client) = {

    monResult.success(client.userId.value).increment()

    work.acquiredAt foreach { acquiredAt =>
      lila.mon.shoginet.queueTime(if (work.sender.system) "system" else "user").record {
        acquiredAt.getMillis - work.createdAt.getMillis
      }
    }
  }

  private[shoginet] def failure(work: Work, client: Client, e: Exception) = {
    logger.warn(
      s"Received invalid ${work.name} ${work.id} for ${work.game.id} by ${client.fullId}",
      e,
    )
    monResult.failure(client.userId.value).increment()
  }

  private[shoginet] def timeout(userId: Client.UserId) =
    monResult.timeout(userId.value).increment()

  private[shoginet] def abort(client: Client) =
    monResult.abort(client.userId.value).increment()

  private[shoginet] def notFound(id: Work.Id, name: String, client: Client) = {
    logger.info(s"Received unknown ${name} $id by ${client.fullId}")
    monResult.notFound(client.userId.value).increment()
  }

  private[shoginet] def notAcquired(work: Work, client: Client) = {
    logger.info(
      s"Received unacquired ${work.name} ${work.id} for ${work.game.id} by ${client.fullId}. Work current tries: ${work.tries} acquired: ${work.acquired}",
    )
    monResult.notAcquired(client.userId.value).increment()
  }

  private[shoginet] def newPuzzle(puzzleId: String, client: Client) = {
    logger.info(s"Added new puzzle #${puzzleId} by ${client.fullId}")
  }
}
