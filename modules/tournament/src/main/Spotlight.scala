package lila.tournament

import org.joda.time.DateTime

import lila.user.User

case class Spotlight(
    headline: String,
    description: String,
    homepageHours: Option[Int] = None, // feature on homepage hours before start
)

object Spotlight {

  import Schedule.Freq._

  def select(tours: List[Tournament], user: Option[User], max: Int): List[Tournament] =
    sort(filter(tours, user)) take max

  private def sort(tours: List[Tournament]) =
    tours.sortBy { t =>
      -(if (t.schedule.isDefined) 100 else t.nbPlayers) // ugly...
    }

  private def filter(tours: List[Tournament], user: Option[User]) =
    tours.filter { t =>
      !t.isFinished &&
      user.fold(true)(validVariant(t, _)) &&
      t.spotlight
        .flatMap(_.homepageHours)
        .map(hours => t.startsAt.minusHours(hours).isBeforeNow)
        .getOrElse(automatically(t, user.isEmpty))
    }

  private def automatically(tour: Tournament, isAnon: Boolean): Boolean =
    tour.schedule.fold(
      !isAnon && (tour.popular || tour.isNowOrSoon) && (tour.notFull || tour.nearImportantMoment),
    ) { schedule =>
      tour.startsAt isBefore DateTime.now.plusMinutes {
        schedule.freq match {
          case Unique           => 5 * 24 * 60
          case Yearly           => 3 * 24 * 60
          case Monthly | Shield => 36 * 60
          case Weekend          => 12 * 60
          case Weekly           => 6 * 60
          case Daily            => 2 * 60
          case _                => 30
        }
      }
    }

  private def validVariant(tour: Tournament, user: User): Boolean =
    tour.variant.standard || user.perfs(tour.perfType).latest ?? { l =>
      l.plusWeeks(4).isAfterNow
    }

}
