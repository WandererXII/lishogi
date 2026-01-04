package lila.activity

import lila.common.Day
import lila.db.dsl._
import lila.db.ignoreDuplicateKey
import lila.game.Game
import lila.study.Study
import lila.user.User

final class ActivityWriteApi(
    coll: Coll,
    studyApi: lila.study.StudyApi,
)(implicit ec: scala.concurrent.ExecutionContext) {

  import BSONHandlers._
  import activities._
  import model._

  def game(game: Game): Funit =
    game.userIds
      .flatMap { userId =>
        game.playerByUserId(userId) map { player =>
          updateToday(userId) { a =>
            if (!game.isCorrespondence)
              a.copy(
                games = a.games.orDefault
                  .add(game.perfType, Score.make(game wonBy player.color, RatingProg make player))
                  .some,
              )
            else
              a.copy(
                corres = a.corres.orDefault.add(GameId(game.id), false, true).some,
              )
          }
        }
      }
      .sequenceFu
      .void

  def forumPost(post: lila.forum.Post): Funit =
    post.userId.filter(User.lishogiId !=) ?? { userId =>
      updateToday(userId) { a =>
        a.copy(
          posts = (~a.posts + PostId(post.id)).some,
        )
      }
    }

  def puzzle(res: lila.puzzle.Puzzle.UserResult): Funit =
    updateToday(res.userId) { a =>
      a.copy(
        puzzles = (~a.puzzles + Score.make(
          res = res.result.win.some,
          rp = RatingProg(Rating(res.rating._1), Rating(res.rating._2)).some,
        )).some,
      )
    }

  def storm(userId: User.ID, score: Int): Funit =
    updateToday(userId) { a =>
      a.copy(
        storm = (~a.storm + score).some,
      )
    }

  def practice(prog: lila.practice.PracticeProgress.OnComplete) =
    updateToday(prog.userId) { a =>
      a.copy(practice = (~a.practice + prog.studyId).some)
    }

  def simul(simul: lila.simul.Simul) =
    simulParticipant(simul, simul.hostId) >>
      simul.pairings.map(_.player.user).map { simulParticipant(simul, _) }.sequenceFu.void

  private def simulParticipant(simul: lila.simul.Simul, userId: String) =
    updateToday(userId) { a =>
      a.copy(simuls = (~a.simuls + SimulId(simul.id)).some)
    }

  def corresMove(gameId: Game.ID, userId: User.ID) =
    updateToday(userId) { a =>
      a.copy(corres = ((~a.corres).add(GameId(gameId), true, false)).some)
    }

  def plan(userId: User.ID, months: Int) =
    updateToday(userId) { a =>
      a.copy(patron = (Patron(months)).some)
    }

  def follow(from: User.ID, to: User.ID) =
    updateToday(from) { a =>
      a.copy(follows = (~a.follows addOut to).some)
    } >>
      updateToday(to) { a =>
        a.copy(follows = (~a.follows addIn from).some)
      }

  def unfollowAll(from: User, following: Set[User.ID]) =
    coll.secondaryPreferred.distinctEasy[User.ID, Set](
      "f.o.ids",
      byUser(from.id),
    ) flatMap { extra =>
      val all = following ++ extra
      all.nonEmpty.?? {
        logger.info(s"${from.id} unfollow ${all.size} users")
        all
          .map { userId =>
            coll.update.one(
              byUser(userId) ++ $doc("f.i.ids" -> from.id),
              $pull("f.i.ids" -> from.id),
            )
          }
          .sequenceFu
          .void
      }
    }

  def study(id: Study.Id) =
    studyApi byId id flatMap {
      _.filter(_.isPublic) ?? { s =>
        updateToday(s.ownerId) { a =>
          a.copy(studies = (~a.studies + s.id).some)
        }
      }
    }

  def team(id: String, userId: User.ID) =
    updateToday(userId) { a =>
      a.copy(teams = (~a.teams + id).some)
    }

  def streamStart(userId: User.ID) =
    updateToday(userId) { _.copy(stream = true) }

  def erase(user: User) = coll.delete.one(byUser(user.id))

  private def getToday(userId: User.ID): Fu[Option[Activity]] =
    coll.one[Activity](byUser(userId) ++ $doc(ActivityFields.day -> Day.today.value))
  private def getOrCreateToday(userId: User.ID): Fu[Activity] =
    getToday(userId) map { _ | Activity.make(userId) }
  private def updateToday(userId: User.ID)(f: Activity => Activity): Funit =
    getOrCreateToday(userId) flatMap { old =>
      val updated = f(old)
      coll.update.one($id(updated.id), updated, upsert = true).void.recover(ignoreDuplicateKey)
    }
}
