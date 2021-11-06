package lishogi.activity

import org.joda.time.Interval

import lishogi.game.LightPov
import lishogi.practice.PracticeStudy
import lishogi.simul.Simul
import lishogi.study.Study
import lishogi.tournament.LeaderboardApi.{ Entry => TourEntry }

import activities._
import model._

case class ActivityView(
    interval: Interval,
    games: Option[Games] = None,
    puzzles: Option[Puzzles] = None,
    storm: Option[Storm] = None,
    practice: Option[Map[PracticeStudy, Int]] = None,
    simuls: Option[List[Simul]] = None,
    patron: Option[Patron] = None,
    posts: Option[Map[lishogi.forum.Topic, List[lishogi.forum.Post]]] = None,
    corresMoves: Option[(Int, List[LightPov])] = None,
    corresEnds: Option[(Score, List[LightPov])] = None,
    follows: Option[Follows] = None,
    studies: Option[List[Study.IdName]] = None,
    teams: Option[Teams] = None,
    tours: Option[ActivityView.Tours] = None,
    stream: Boolean = false,
    signup: Boolean = false
)

object ActivityView {

  case class Tours(
      nb: Int,
      best: List[TourEntry]
  )
}
