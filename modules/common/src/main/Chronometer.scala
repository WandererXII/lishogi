package lishogi.common

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

object Chronometer {

  case class Lap[A](result: A, nanos: Long) {

    def millis = (nanos / 1000000).toInt
    def micros = (nanos / 1000).toInt

    def logIfSlow(threshold: Int, logger: lishogi.log.Logger)(msg: A => String) = {
      if (millis >= threshold) log(logger)(msg)
      else this
    }
    def log(logger: lishogi.log.Logger)(msg: A => String) = {
      logger.info(s"<${millis}ms> ${msg(result)}")
      this
    }

    def mon(path: lishogi.mon.TimerPath) = {
      path(lishogi.mon).record(nanos)
      this
    }

    def monValue(path: A => lishogi.mon.TimerPath) = {
      path(result)(lishogi.mon).record(nanos)
      this
    }

    def pp: A = {
      println(s"chrono $showDuration")
      result
    }

    def pp(msg: String): A = {
      println(s"chrono $msg - $showDuration")
      result
    }
    def ppIfGt(msg: String, duration: FiniteDuration): A =
      if (nanos > duration.toNanos) pp(msg)
      else result

    def showDuration: String = if (millis >= 1) s"$millis ms" else s"$micros micros"
  }
  case class LapTry[A](result: Try[A], nanos: Long)

  case class FuLap[A](lap: Fu[Lap[A]]) extends AnyVal {

    def logIfSlow(threshold: Int, logger: lishogi.log.Logger)(msg: A => String) = {
      lap.dforeach(_.logIfSlow(threshold, logger)(msg))
      this
    }

    def mon(path: lishogi.mon.TimerPath) = {
      lap dforeach { l =>
        path(lishogi.mon).record(l.nanos)
      }
      this
    }

    def monValue(path: A => lishogi.mon.TimerPath) = {
      lap dforeach { _.monValue(path) }
      this
    }

    def log(logger: lishogi.log.Logger)(msg: A => String) = {
      lap.dforeach(_.log(logger)(msg))
      this
    }

    def pp: Fu[A]                                            = lap.dmap(_.pp)
    def pp(msg: String): Fu[A]                               = lap.dmap(_ pp msg)
    def ppIfGt(msg: String, duration: FiniteDuration): Fu[A] = lap.dmap(_.ppIfGt(msg, duration))

    def result = lap.dmap(_.result)
  }

  case class FuLapTry[A](lap: Fu[LapTry[A]]) extends AnyVal {

    def mon(path: Try[A] => kamon.metric.Timer) = {
      lap.dforeach { l =>
        path(l.result).record(l.nanos)
      }
      this
    }

    def result =
      lap.flatMap { l =>
        Future.fromTry(l.result)
      }(ExecutionContext.parasitic)
  }

  def apply[A](f: Fu[A]): FuLap[A] = {
    val start = nowNanos
    FuLap(f dmap { Lap(_, nowNanos - start) })
  }

  def lapTry[A](f: Fu[A]): FuLapTry[A] = {
    val start = nowNanos
    FuLapTry {
      f.transformWith { r =>
        fuccess(LapTry(r, nowNanos - start))
      }(ExecutionContext.parasitic)
    }
  }

  def sync[A](f: => A): Lap[A] = {
    val start = nowNanos
    val res   = f
    Lap(res, nowNanos - start)
  }

  def syncEffect[A](f: => A)(effect: Lap[A] => Unit): A = {
    val lap = sync(f)
    effect(lap)
    lap.result
  }

  def syncMon[A](path: lishogi.mon.TimerPath)(f: => A): A = {
    val timer = path(lishogi.mon).start()
    val res   = f
    timer.stop()
    res
  }
}
