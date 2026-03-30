package lila.mod

import lila.chat.Chat
import lila.report.Suspect
import lila.simul.Simul
import lila.tournament.Tournament

final class PublicChat(
    chatApi: lila.chat.ChatApi,
    tournamentApi: lila.tournament.TournamentApi,
    simulEnv: lila.simul.Env,
)(implicit ec: scala.concurrent.ExecutionContext) {

  def all: Fu[(List[(Tournament, Chat)], List[(Simul, Chat)])] =
    tournamentChats zip simulChats

  def delete(suspect: Suspect): Funit =
    all.flatMap { case (tours, simuls) =>
      (tours.map(_._2) ::: simuls.map(_._2))
        .filter(_ hasLinesOf suspect.user)
        .map(chatApi.delete(_, suspect.user, _.Global))
        .sequenceFu
        .void
    }

  // only auto scheduled
  private def tournamentChats: Fu[List[(Tournament, Chat)]] =
    tournamentApi.startedScheduled.flatMap { tours =>
      val ids = tours.map(_.id) map Chat.Id.apply
      chatApi.findAll(ids).map { chats =>
        chats.map { chat =>
          tours.find(_.id == chat.id.value).map(tour => (tour, chat))
        }.flatten
      } map sortTournamentsByRelevance
    }

  private def simulChats: Fu[List[(Simul, Chat)]] =
    fetchVisibleSimuls.flatMap { simuls =>
      val ids = simuls.map(_.id) map Chat.Id.apply
      chatApi.findAll(ids).map { chats =>
        chats.map { chat =>
          simuls.find(_.id == chat.id.value).map(simul => (simul, chat))
        }.flatten
      }
    }

  private def fetchVisibleSimuls: Fu[List[Simul]] = {
    simulEnv.allCreatedFeaturable.get {} zip
      simulEnv.repo.allStarted zip
      simulEnv.repo.allFinishedFeaturable(3) map { case ((created, started), finished) =>
        created ::: started ::: finished
      }
  }

  /** Sort the tournaments by the tournaments most likely to require moderation attention
    */
  private def sortTournamentsByRelevance(tournaments: List[(Tournament, Chat)]) =
    tournaments.sortBy(-_._1.nbPlayers)
}
