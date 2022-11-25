package lila.activity

import alleycats.Zero

import lila.rating.PerfType
import lila.study.Study
import lila.user.User
import model._

object activities {

  val maxSubEntries = 15

  case class Games(value: Map[PerfType, Score]) extends AnyVal {
    def add(pt: PerfType, score: Score) =
      copy(
        value = value + (pt -> value.get(pt).fold(score)(_ add score))
      )
    def hasNonCorres = value.exists(_._1 != PerfType.Correspondence)
  }
  implicit val GamesZero = Zero(Games(Map.empty))

  case class Posts(value: List[PostId]) extends AnyVal {
    def +(postId: PostId) = Posts(postId :: value)
  }
  case class PostId(value: String) extends AnyVal
  implicit val PostsZero = Zero(Posts(Nil))

  case class Puzzles(score: Score) {
    def +(s: Score) = Puzzles(score = score add s)
  }
  implicit val PuzzlesZero = Zero(Puzzles(ScoreZero.zero))

  case class Storm(runs: Int, score: Int) {
    def +(s: Int) = Storm(runs = runs + 1, score = score atLeast s)
  }
  implicit val StormZero = Zero(Storm(0, 0))

  case class Practice(value: Map[Study.Id, Int]) {
    def +(studyId: Study.Id) =
      copy(
        value = value + (studyId -> value.get(studyId).fold(1)(1 +))
      )
  }
  implicit val PracticeZero = Zero(Practice(Map.empty))

  case class SimulId(value: String) extends AnyVal
  case class Simuls(value: List[SimulId]) extends AnyVal {
    def +(s: SimulId) = copy(value = s :: value)
  }
  implicit val SimulsZero = Zero(Simuls(Nil))

  case class Corres(moves: Int, movesIn: List[GameId], end: List[GameId]) {
    def add(gameId: GameId, moved: Boolean, ended: Boolean) =
      Corres(
        moves = moves + (moved ?? 1),
        movesIn = if (moved) (gameId :: movesIn).distinct.take(maxSubEntries) else movesIn,
        end = if (ended) (gameId :: end).take(maxSubEntries) else end
      )
  }
  implicit val CorresZero = Zero(Corres(0, Nil, Nil))

  case class Patron(months: Int) extends AnyVal

  case class Follows(in: Option[FollowList], out: Option[FollowList]) {
    def addIn(id: User.ID)  = copy(in = Some(~in + id))
    def addOut(id: User.ID) = copy(out = Some(~out + id))
    def isEmpty             = in.fold(true)(_.isEmpty) && out.fold(true)(_.isEmpty)
  }
  case class FollowList(ids: List[User.ID], nb: Option[Int]) {
    def actualNb = nb | ids.size
    def +(id: User.ID) =
      if (ids contains id) this
      else {
        val newIds = (id :: ids).distinct
        copy(
          ids = newIds take maxSubEntries,
          nb = nb.map(1 +).orElse(newIds.size > maxSubEntries option newIds.size)
        )
      }
    def isEmpty = ids.isEmpty
  }
  implicit val FollowListZero = Zero(FollowList(Nil, None))
  implicit val FollowsZero    = Zero(Follows(None, None))

  case class Studies(value: List[Study.Id]) extends AnyVal {
    def +(s: Study.Id) = copy(value = (s :: value) take maxSubEntries)
  }
  implicit val StudiesZero = Zero(Studies(Nil))

  case class Teams(value: List[String]) extends AnyVal {
    def +(s: String) = copy(value = (s :: value).distinct take maxSubEntries)
  }
  implicit val TeamsZero = Zero(Teams(Nil))
}
