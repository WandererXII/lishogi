package lila.activity

import org.joda.time.Interval

import lila.activity.activities._
import lila.common.Day
import lila.user.User

case class Activity(
    id: Activity.ID,
    userId: User.ID,
    day: Day,
    games: Option[Games] = None,
    posts: Option[Posts] = None,
    puzzles: Option[Puzzles] = None,
    storm: Option[Storm] = None,
    practice: Option[Practice] = None,
    simuls: Option[Simuls] = None,
    corres: Option[Corres] = None,
    patron: Option[Patron] = None,
    follows: Option[Follows] = None,
    studies: Option[Studies] = None,
    teams: Option[Teams] = None,
    stream: Boolean = false,
) {

  def date = day.toDate

  def interval = new Interval(date, date plusDays 1)

  def isEmpty =
    !stream && List(
      games,
      posts,
      puzzles,
      storm,
      practice,
      simuls,
      corres,
      patron,
      follows,
      studies,
      teams,
    )
      .forall(_.isEmpty)
}

object Activity {

  type ID = String

  val expireAfterDays = 15

  def make(userId: User.ID) =
    Activity(
      id = lila.common.ThreadLocalRandom nextString 8,
      userId = userId,
      day = Day.today,
    )
}
