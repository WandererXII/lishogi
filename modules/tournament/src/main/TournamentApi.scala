package lila.tournament

import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.util.chaining._

import akka.actor.ActorSystem
import akka.actor.Props
import akka.stream.scaladsl._
import org.joda.time.DateTime

import lila.common.Bus
import lila.common.LightUser
import lila.common.config.MaxPerPage
import lila.common.config.MaxPerSecond
import lila.common.paginator.Paginator
import lila.game.Game
import lila.game.GameRepo
import lila.game.LightPov
import lila.game.Pov
import lila.hub.LightTeam
import lila.hub.LightTeam._
import lila.round.actorApi.round.AbortForce
import lila.round.actorApi.round.GoBerserk
import lila.user.User
import lila.user.UserRepo

final class TournamentApi(
    cached: Cached,
    userRepo: UserRepo,
    gameRepo: GameRepo,
    playerRepo: PlayerRepo,
    pairingRepo: PairingRepo,
    arrangementRepo: ArrangementRepo,
    tournamentRepo: TournamentRepo,
    autoPairing: AutoPairing,
    pairingSystem: arena.PairingSystem,
    callbacks: TournamentApi.Callbacks,
    tellRound: lila.round.TellRound,
    roundSocket: lila.round.RoundSocket,
    trophyApi: lila.user.TrophyApi,
    verify: Condition.Verify,
    duelStore: DuelStore,
    pause: Pause,
    cacheApi: lila.memo.CacheApi,
    lightUserApi: lila.user.LightUserApi,
    proxyRepo: lila.round.GameProxyRepo,
    notifier: Notifier,
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: ActorSystem,
) {

  private val workQueue =
    new lila.hub.DuctSequencers(
      maxSize = 256,
      expiration = 1 minute,
      timeout = 10 seconds,
      name = "tournament",
    )

  def get(id: Tournament.ID) = tournamentRepo byId id

  def idToName(id: Tournament.ID): Fu[Option[String]] =
    tournamentRepo.byId(id) dmap2 { _.name }

  def createTournament(
      setup: TournamentSetup,
      me: User,
      myTeams: List[LightTeam],
      getUserTeamIds: User.ID => Fu[List[TeamID]],
      andJoin: Boolean = true,
  ): Fu[Tournament] = {
    val tour = Tournament.make(
      by = Right(me),
      name = setup.name,
      format = setup.realFormat,
      timeControl = setup.timeControlSetup.convert,
      minutes = setup.realMinutes,
      startDate = setup.realStartDate,
      mode = setup.realMode,
      password = setup.password,
      candidatesOnly = setup.candidatesOnly | false,
      variant = setup.realVariant,
      position = setup.position,
      berserkable = setup.berserkable | true,
      streakable = setup.streakable | true,
      teamBattle = setup.teamBattleByTeam map TeamBattle.init,
      description = setup.description,
      hasChat = setup.hasChat | true,
    ) pipe { tour =>
      tour.copy(conditions = setup.conditions.convert(myTeams.view.map(_.pair).toMap))
    }
    tournamentRepo.insert(tour) >> {
      setup.teamBattleByTeam.orElse(tour.conditions.teamMember.map(_.teamId)).?? { teamId =>
        tournamentRepo.setForTeam(tour.id, teamId).void
      }
    } >> {
      andJoin ?? join(
        tour.id,
        me,
        tour.password,
        setup.teamBattleByTeam,
        getUserTeamIds,
        isLeader = false,
        none,
      )
    } inject tour
  }

  def update(old: Tournament, data: TournamentSetup, myTeams: List[LightTeam]): Funit = {
    val tour = old
      .copy(
        name = data.name | old.name,
        timeControl = if (old.isCreated) data.timeControlSetup.convert else old.timeControl,
        minutes = data.realMinutes,
        mode = data.realMode,
        variant = if (old.isCreated) data.realVariant else old.variant,
        startsAt = data.startDate | old.startsAt,
        password = data.password,
        candidatesOnly = ~data.candidatesOnly,
        position =
          if (old.isCreated || old.position.isDefined)
            data.position
          else old.position,
        noBerserk = !(~data.berserkable),
        noStreak = !(~data.streakable),
        teamBattle = old.teamBattle,
        description = data.description,
        hasChat = data.hasChat | true,
      ) pipe { tour =>
      tour.copy(
        conditions = data.conditions
          .convert(myTeams.view.map(_.pair).toMap)
          .copy(teamMember = old.conditions.teamMember), // can't change that
        mode = if (tour.position.isDefined) shogi.Mode.Casual else tour.mode,
      )
    }
    tournamentRepo update tour void
  }

  def teamBattleUpdate(
      tour: Tournament,
      data: TeamBattle.DataForm.Setup,
      filterExistingTeamIds: Set[TeamID] => Fu[Set[TeamID]],
  ): Funit =
    filterExistingTeamIds(data.potentialTeamIds) flatMap { teamIds =>
      tournamentRepo.setTeamBattle(tour.id, TeamBattle(teamIds, data.nbLeaders))
    }

  def teamBattleTeamInfo(tour: Tournament, teamId: TeamID): Fu[Option[TeamBattle.TeamInfo]] =
    tour.teamBattle.exists(_ teams teamId) ?? cached.teamInfo.get(tour.id -> teamId)

  private val hadPairings = new lila.memo.ExpireSetMemo(1 hour)

  private[tournament] def makePairings(
      forTour: Tournament,
      users: WaitingUsers,
      smallTourNbActivePlayers: Option[Int],
  ): Funit =
    (users.size > 1 && forTour.isArena && (
      !hadPairings.get(forTour.id) ||
        users.haveWaitedEnough ||
        smallTourNbActivePlayers.exists(_ <= users.size * 1.5)
    )) ??
      Sequencing(forTour.id)(tournamentRepo.startedById) { tour =>
        cached
          .ranking(tour)
          .mon(_.tournament.pairing.createRanking)
          .flatMap { ranking =>
            pairingSystem
              .createPairings(tour, users, ranking, smallTourNbActivePlayers)
              .mon(_.tournament.pairing.createPairings)
              .flatMap {
                case Nil => funit
                case pairings =>
                  hadPairings put tour.id
                  makePlayerMap(tour.id, pairings.flatMap(_.users))
                    .mon(_.tournament.pairing.createPlayerMap)
                    .flatMap { playersMap =>
                      pairings
                        .map { pairing =>
                          pairingRepo.insert(pairing) >>
                            autoPairing(tour, pairing, playersMap, ranking)
                              .mon(_.tournament.pairing.createAutoPairing)
                              .map { game => socket.foreach(_.startGame(tour.id, game)) }
                        }
                        .sequenceFu
                        .mon(_.tournament.pairing.createInserts) >>
                        featureOneOf(tour, pairings, ranking)
                          .mon(_.tournament.pairing.createFeature) >>-
                        lila.mon.tournament.pairing.batchSize.record(pairings.size).unit
                    }
              }
          }
          .monSuccess(_.tournament.pairing.create)
          .chronometer
          .logIfSlow(100, logger)(_ => s"Pairings for https://lishogi.org/tournament/${tour.id}")
          .result
      }

  private def featureOneOf(tour: Tournament, pairings: Pairings, ranking: Ranking): Funit =
    tour.featuredId.ifTrue(pairings.nonEmpty) ?? pairingRepo.byId map2
      RankedPairing(ranking) map (_.flatten) flatMap { curOption =>
        pairings.flatMap(RankedPairing(ranking)).sortBy(_.bestRank).headOption ?? { bestCandidate =>
          def switch = tournamentRepo.setFeaturedGameId(tour.id, bestCandidate.pairing.gameId)
          curOption.filter(_.pairing.playing) match {
            case Some(current) if bestCandidate.bestRank < current.bestRank => switch
            case Some(_)                                                    => funit
            case _                                                          => switch
          }
        }
      }

  private[tournament] def start(oldTour: Tournament): Funit =
    Sequencing(oldTour.id)(tournamentRepo.createdById) { tour =>
      tournamentRepo.setStatus(tour.id, Status.Started) >>-
        socket.foreach(_.reload(tour.id))
    }

  private[tournament] def destroy(tour: Tournament): Funit =
    tournamentRepo.remove(tour).void >>
      (if (tour.isArena) pairingRepo.removeByTour(tour.id)
       else arrangementRepo.removeByTour(tour.id)) >>
      playerRepo.removeByTour(tour.id) >>- socket.foreach(_.reload(tour.id))

  private[tournament] def finish(oldTour: Tournament): Funit =
    Sequencing(oldTour.id)(tournamentRepo.startedById) { tour =>
      (if (tour.isArena) pairingRepo.count(tour.id)
       else arrangementRepo.countWithGame(tour.id)) flatMap {
        case 0 => destroy(tour)
        case _ =>
          for {
            _ <- tournamentRepo.setStatus(tour.id, Status.Finished)
            _ <- tour.candidates.nonEmpty ?? tournamentRepo.clearCandidates(tour.id)
            _ <- playerRepo unWithdraw tour.id
            _ <-
              if (tour.isArena) pairingRepo.removePlaying(tour.id)
              else if (tour.isOrganized) arrangementRepo.clearUnfinished(tour.id)
              else arrangementRepo.removeUnfinished(tour.id)
            winner <- playerRepo winner tour.id
            _      <- winner.??(p => tournamentRepo.setWinnerId(tour.id, p.userId))
          } yield {
            callbacks.clearJsonViewCache(tour)
            socket.foreach(_.finish(tour.id))
            playerRepo withPoints tour.id foreach {
              _ foreach { p =>
                userRepo.incToints(p.userId, p.score)
              }
            }
            awardTrophies(tour).logFailure(logger, _ => s"${tour.id} awardTrophies")
            callbacks.indexLeaderboard(tour).logFailure(logger, _ => s"${tour.id} indexLeaderboard")
            callbacks.clearWinnersCache(tour)
            callbacks.clearTrophyCache(tour)
            duelStore.remove(tour)
          }
      }
    }

  private[tournament] val killSchedule = scala.collection.mutable.Set.empty[Tournament.ID]

  def kill(tour: Tournament): Funit = {
    if (tour.isStarted) fuccess(killSchedule add tour.id).void
    else if (tour.isCreated) destroy(tour)
    else funit
  }

  private def awardTrophies(tour: Tournament): Funit = {
    import lila.user.TrophyKind._
    import lila.tournament.Tournament.tournamentUrl
    tour.schedule.??(_.freq == Schedule.Freq.Marathon) ?? {
      playerRepo.bestByTourWithRank(tour.id, 100).flatMap {
        _.map {
          case rp if rp.rank == 1 =>
            trophyApi.award(tournamentUrl(tour.id), rp.player.userId, marathonWinner)
          case rp if rp.rank <= 10 =>
            trophyApi.award(tournamentUrl(tour.id), rp.player.userId, marathonTopTen)
          case rp if rp.rank <= 50 =>
            trophyApi.award(tournamentUrl(tour.id), rp.player.userId, marathonTopFifty)
          case rp => trophyApi.award(tournamentUrl(tour.id), rp.player.userId, marathonTopHundred)
        }.sequenceFu.void
      }
    }
  }

  def verdicts(
      tour: Tournament,
      me: Option[User],
      getUserTeamIds: User.ID => Fu[List[TeamID]],
  ): Fu[Condition.All.WithVerdicts] =
    me match {
      case None => fuccess(tour.conditions.accepted)
      case Some(user) =>
        {
          tour.isStarted ?? playerRepo.exists(tour.id, user.id)
        } flatMap {
          case true => fuccess(tour.conditions.accepted)
          case _    => verify(tour.conditions, user, tour.perfType, getUserTeamIds)
        }
    }

  def closeJoining(
      tourId: Tournament.ID,
      close: Boolean,
      by: User.ID,
  ): Funit = Sequencing(tourId)(tournamentRepo.enterableById) { tour =>
    (tour.createdBy == by) ??
      tournamentRepo.setClosed(tourId, close) >>-
      socket.foreach(_.reload(tour.id))
  }

  def processCandidate(
      tourId: Tournament.ID,
      userId: User.ID,
      accept: Boolean,
      by: User.ID,
  ): Funit =
    Sequencing(tourId)(tournamentRepo.enterableById) { tour =>
      val askedToJoin = tour.candidates.contains(userId) || tour.denied.contains(userId)
      (tour.createdBy == by && askedToJoin && tour.notFull) ?? {
        userRepo.byId(userId) flatMap {
          _ ?? { user =>
            val updatedCandidates = tour.candidates.filterNot(_ == user.id)
            if (accept)
              playerRepo.join(
                tour.id,
                user,
                tour.perfType,
                none,
                useOrder = tour.isRobin,
              ) >> tournamentRepo.setProcessedCandidate(
                tour.id,
                candidates = updatedCandidates,
                denied = tour.denied.filterNot(_ == user.id),
              ) >> updateNbPlayers(tour.id) >>- {
                if (tour.hasArrangements) cached.arrangement.invalidatePlayers(tour.id)
                socket.foreach(_.reload(tour.id))
              }
            else
              tournamentRepo.setProcessedCandidate(
                tour.id,
                candidates = updatedCandidates,
                denied = userId :: tour.denied,
              ) >>- socket.foreach(_.reloadUsers(tour.id, List(userId, by)))
          }
        }
      }
    }

  private[tournament] def join(
      tourId: Tournament.ID,
      me: User,
      password: Option[String],
      withTeamId: Option[String],
      getUserTeamIds: User.ID => Fu[List[TeamID]],
      isLeader: Boolean,
      promise: Option[Promise[Boolean]],
  ): Funit =
    Sequencing(tourId)(tournamentRepo.enterableById) { tour =>
      playerRepo.exists(tour.id, me.id) flatMap { playerExists =>
        val fuJoined =
          if (
            ((tour.password == password && !tour.closed && tour.notFull) || playerExists) &&
            !tour.denied.contains(me.id)
          ) {
            verdicts(tour, me.some, getUserTeamIds) flatMap {
              _.accepted ?? {
                pause.canJoin(me.id, tour) ?? {
                  def proceedAsCandidate =
                    !tour.candidatesFull ?? (tournamentRepo.setCandidates(
                      tour.id,
                      me.id :: tour.candidates,
                    ) >>-
                      socket.foreach(
                        _.reloadUsers(tour.id, List(me.id, tour.createdBy)),
                      )) inject !tour.candidatesFull
                  def proceedWithTeam(team: Option[String]) =
                    playerRepo.join(
                      tour.id,
                      me,
                      tour.perfType,
                      team,
                      useOrder = tour.isRobin,
                    ) >>
                      updateNbPlayers(tour.id) >>- {
                        if (tour.hasArrangements) cached.arrangement.invalidatePlayers(tour.id)
                        socket.foreach(_.reload(tour.id))
                      } inject true
                  if (tour.candidatesOnly && !playerExists)
                    proceedAsCandidate
                  else
                    withTeamId match {
                      case None if tour.isTeamBattle => playerExists ?? proceedWithTeam(none)
                      case None                      => proceedWithTeam(none)
                      case Some(team) =>
                        tour.teamBattle match {
                          case Some(battle) if battle.teams contains team =>
                            getUserTeamIds(me.id) flatMap { myTeams =>
                              if (myTeams has team) proceedWithTeam(team.some)
                              else fuccess(false)
                            }
                          case _ => fuccess(false)
                        }
                    }
                }
              }
            }
          } else {
            socket.foreach(_.reload(tour.id))
            fuccess(false)
          }
        fuJoined map { joined =>
          withTeamId.ifTrue(joined && isLeader && tour.isTeamBattle) foreach {
            tournamentRepo.setForTeam(tour.id, _)
          }
          promise.foreach(_ success joined)
        }
      }
    }

  def joinWithResult(
      tourId: Tournament.ID,
      me: User,
      password: Option[String],
      teamId: Option[String],
      getUserTeamIds: User.ID => Fu[List[TeamID]],
      isLeader: Boolean,
  ): Fu[Boolean] = {
    val promise = Promise[Boolean]()
    join(tourId, me, password, teamId, getUserTeamIds, isLeader, promise.some)
    promise.future.withTimeoutDefault(5.seconds, false)
  }

  def arrangementMatch(
      lookup: Arrangement.Lookup,
      userId: User.ID,
      join: Boolean,
  ): Funit =
    ArrangementUpdate(
      lookup,
      userId,
      (tour, arr) =>
        (
          tour.isStarted &&
            !arr.hasGame &&
            arr.hasUser(userId)
        ),
    ) { (tour, arrangement) =>
      if (join && arrangement.opponentUser(userId).exists(_.isReady)) {
        makeManualPairings(tour, arrangement)
      } else if (join || arrangement.user(userId).exists(_.isReady)) {
        val updated = arrangement.setReadyAt(userId, join ?? DateTime.now.some)
        arrangementRepo.update(updated) >>- cached.arrangement.invalidateArrangaments(
          arrangement.tourId,
        ) >>-
          socket.foreach(_.arrangementChange(updated))
      } else funit
    }

  private def makePlayerMap(tourId: Tournament.ID, users: List[User.ID]): Fu[Map[User.ID, Player]] =
    playerRepo
      .byTourAndUserIds(tourId, users)
      .map {
        _.view
          .map { player =>
            player.userId -> player
          }
          .toMap
      }

  private def makeManualPairings(
      tour: Tournament,
      arrangement: Arrangement,
  ): Funit =
    makePlayerMap(tour.id, arrangement.userIds)
      .mon(_.tournament.arrangement.createPlayerMap)
      .flatMap { playersMap =>
        autoPairing(tour, arrangement, playersMap) flatMap { game =>
          arrangementRepo.update(
            arrangement.startGame(
              game.id,
              if (game.sentePlayer.userId.exists(_ == arrangement.user1.id)) shogi.Color.Sente
              else shogi.Color.Gote,
            ),
          ) >>-
            socket.foreach(_.startGame(tour.id, game)) >>-
            featureOneOf(tour, game.id, playersMap.values)
              .mon(_.tournament.arrangement.createFeature)
              .unit
        }
      }
      .monSuccess(_.tournament.arrangement.create)
      .chronometer
      .logIfSlow(75, logger)(_ => s"Robin pairing for https://lishogi.org/tournament/${tour.id}")
      .result

  private def featureOneOf(
      tour: Tournament,
      gameId: Game.ID,
      players: Iterable[Player],
  ): Funit = {
    tour.featuredId ?? { gid => arrangementRepo.byGame(tour.id, gid) } flatMap { arrOpt =>
      def switch = tournamentRepo.setFeaturedGameId(tour.id, gameId)
      val fuOptOtherPlayers =
        arrOpt.filter(_.playing) ?? { arr =>
          makePlayerMap(tour.id, arr.userIds).map(_.values.some)
        }
      fuOptOtherPlayers flatMap { playersOpt =>
        val curScore = playersOpt.flatMap(_.map(_.score).maxOption)
        val newScore = ~players.map(_.score).maxOption
        if (curScore.exists(_ > newScore)) funit
        else switch
      }
    }
  }

  def arrangementSetTime(
      lookup: Arrangement.Lookup,
      userId: User.ID,
      dateTime: Option[DateTime],
  ): Funit =
    ArrangementUpdate(
      lookup,
      userId,
      (_, arr) => (!arr.hasGame && arr.hasUser(userId) && !arr.lockedScheduledAt),
    ) { (_, arrangement) =>
      val updated      = arrangement.setScheduledAt(userId, dateTime)
      val scheduledSet = arrangement.scheduledAt.isEmpty && updated.scheduledAt.isDefined
      arrangementRepo.update(updated) >>- cached.arrangement.invalidateArrangaments(
        arrangement.tourId,
      ) >>- socket
        .foreach(_.arrangementChange(updated)) >>- {
        if (scheduledSet) {
          notifier.arrangementsAgreedTimeCache.put(
            updated.id,
          )
          notifier
            .arrangementConfirmation(updated, userId)
            .unit
        }
      }
    }

  def arrangementOrganizerSet(
      lookup: Arrangement.Lookup,
      userId: User.ID,
      settings: Arrangement.Settings,
  ): Funit =
    ArrangementUpdate(lookup, userId, (tour, _) => (tour.createdBy == userId && tour.isOrganized)) {
      (_, arrangement) =>
        val updated = arrangement.setSettings(settings)
        arrangementRepo.update(updated) >>- cached.arrangement.invalidateArrangaments(
          arrangement.tourId,
        ) >>- socket
          .foreach(_.arrangementChange(updated))
    }

  def arrangementDelete(
      lookup: Arrangement.Lookup,
      userId: User.ID,
  ): Funit =
    ArrangementUpdate(
      lookup,
      userId,
      (tour, arr) => (tour.createdBy == userId && tour.isOrganized && !arr.hasGame),
    ) { (_, arrangement) =>
      arrangementRepo.delete(arrangement.id) >>-
        cached.arrangement.invalidateArrangaments(arrangement.tourId) >>-
        socket.foreach(_.reload(arrangement.tourId))
    }

  def pageOf(tour: Tournament, userId: User.ID): Fu[Option[Int]] =
    cached ranking tour map {
      _ get userId map { rank =>
        rank / 10 + 1
      }
    }

  private def updateNbPlayers(tourId: Tournament.ID): Funit =
    playerRepo count tourId flatMap { tournamentRepo.setNbPlayers(tourId, _) }

  def selfPause(tourId: Tournament.ID, userId: User.ID): Funit =
    withdraw(tourId, userId, isPause = true, isStalling = false, isForced = false)

  private def stallPause(tourId: Tournament.ID, userId: User.ID): Funit =
    withdraw(tourId, userId, isPause = false, isStalling = true, isForced = false)

  private def withdraw(
      tourId: Tournament.ID,
      userId: User.ID,
      isPause: Boolean,
      isStalling: Boolean,
      isForced: Boolean,
  ): Funit =
    Sequencing(tourId)(tournamentRepo.enterableById) {
      case tour if tour.candidates.contains(userId) =>
        tournamentRepo.setCandidates(tour.id, tour.candidates.filterNot(_ == userId)) >>- socket
          .foreach(
            _.reloadUsers(tour.id, List(tour.createdBy, userId)),
          )
      case tour if tour.isCreated =>
        playerRepo.remove(tour.id, userId) >> updateNbPlayers(tour.id) >>- socket.foreach(
          _.reload(tour.id),
        )
      case tour if (tour.isStarted && (tour.isArena || isForced)) =>
        for {
          _ <- playerRepo.withdraw(tour.id, userId)
          pausable <-
            if (isPause) cached.ranking(tour).map { _ get userId exists (7 >) }
            else
              fuccess(isStalling)
        } yield {
          if (pausable) pause.add(userId)
          socket.foreach(_.reload(tour.id))
        }
      case _ => funit
    }

  def withdrawAll(user: User): Funit =
    tournamentRepo.withdrawableIds(user.id) flatMap {
      _.map {
        withdraw(_, user.id, isPause = false, isStalling = false, isForced = true)
      }.sequenceFu.void
    }

  private[tournament] def berserk(gameId: Game.ID, userId: User.ID): Funit =
    proxyRepo game gameId flatMap {
      _.filter(_.berserkable) ?? { game =>
        game.tournamentId ?? { tourId =>
          Sequencing(tourId)(tournamentRepo.startedById) { tour =>
            pairingRepo.findPlaying(tour.id, userId) flatMap {
              case Some(pairing) if !pairing.berserkOf(userId) =>
                (pairing colorOf userId) ?? { color =>
                  roundSocket.rounds.ask(gameId) { GoBerserk(color, _) } flatMap {
                    _ ?? pairingRepo.setBerserk(pairing, userId)
                  }
                }
              case _ => funit
            }
          }
        }
      }
    }

  private[tournament] def finishGame(game: Game): Funit =
    game.tournamentId ?? { tourId =>
      Sequencing(tourId)(tournamentRepo.startedById) { tour =>
        if (tour.hasArrangements) {
          arrangementRepo.byGame(tourId, game.id) flatMap {
            _ ?? { arr =>
              arrangementRepo.finish(game, arr) >>
                game.userIds
                  .map(updateArrangementPlayer(tour, game.some, arr))
                  .sequenceFu
                  .void >>- {
                  cached.arrangement.invalidateArrangaments(tourId)
                  cached.arrangement.invalidatePlayers(tourId)
                  socket.foreach(_.reload(tour.id))
                  updateTournamentStanding(tour)
                }
            }
          }
        } else {
          pairingRepo
            .finish(game) >> game.userIds
            .map(updateArenaPlayer(tour, game.some))
            .sequenceFu
            .void >>- {
            duelStore.remove(game)
            socket.foreach(_.reload(tour.id))
            updateTournamentStanding(tour)
            withdrawNonMover(game)
          }
        }
      }
    }

  private[tournament] def sittingDetected(game: Game, player: User.ID): Funit =
    game.tournamentId.ifTrue(game.arrangementId.isEmpty) ?? { stallPause(_, player) }

  private def updateArrangementPlayer(
      tour: Tournament,
      finishing: Option[Game],
      arr: Arrangement,
  )(userId: User.ID): Funit =
    tour.mode.rated ?? { userRepo.perfOf(userId, tour.perfType) } flatMap { perf =>
      playerRepo.update(tour.id, userId) { player =>
        val score: Int = finishing.filter(_.finished) ?? { g =>
          val points = arr.points | Arrangement.Points.default
          if (g.winnerUserId.exists(_ == player.userId)) points.win
          else if (g.winner.isEmpty) points.draw
          else points.loss
        }
        fuccess(
          player.copy(
            score = player.score + score,
            rating = perf.fold(player.rating)(_.intRating),
            provisional = perf.fold(player.provisional)(_.provisional),
          ),
        )
      }
    }

  private def updateArenaPlayer(
      tour: Tournament,
      finishing: Option[Game],
  )(userId: User.ID): Funit =
    tour.mode.rated ?? { userRepo.perfOf(userId, tour.perfType) } flatMap { perf =>
      playerRepo.update(tour.id, userId) { player =>
        cached.sheet.update(tour, player.userId) map { sheet =>
          player.copy(
            score = sheet.total,
            fire = tour.streakable && sheet.onFire,
            rating = perf.fold(player.rating)(_.intRating),
            provisional = perf.fold(player.provisional)(_.provisional),
            performance = {
              for {
                g           <- finishing
                performance <- performanceOf(g, player.userId).map(_.toDouble)
                nbGames = sheet.scores.size
                if nbGames > 0
              } yield Math.round {
                player.performance * (nbGames - 1) / nbGames + performance / nbGames
              } toInt
            } | player.performance,
          )
        }
      }
    }

  private def performanceOf(g: Game, userId: String): Option[Int] =
    for {
      opponent       <- g.opponentByUserId(userId)
      opponentRating <- opponent.rating
      multiplier = g.winnerUserId.??(winner => if (winner == userId) 1 else -1)
    } yield opponentRating + 500 * multiplier

  private def withdrawNonMover(game: Game): Unit =
    if (game.status == shogi.Status.NoStart && game.arrangementId.isEmpty) for {
      tourId <- game.tournamentId
      player <- game.playerWhoDidNotMove
      userId <- player.userId
    } withdraw(tourId, userId, isPause = false, isStalling = false, isForced = false)

  def pausePlaybanned(userId: User.ID) =
    tournamentRepo.withdrawableIds(userId, onlyArena = true) flatMap {
      _.map {
        playerRepo.withdraw(_, userId)
      }.sequenceFu.void
    }

  private[tournament] def kickFromTeam(teamId: TeamID, userId: User.ID): Funit =
    tournamentRepo.withdrawableIds(userId, teamId = teamId.some) flatMap {
      _.map { tourId =>
        Sequencing(tourId)(tournamentRepo.byId) { tour =>
          val fu =
            if (tour.isCreated) playerRepo.remove(tour.id, userId)
            else playerRepo.withdraw(tour.id, userId)
          fu >> updateNbPlayers(tourId) >>- socket.foreach(_.reload(tour.id))
        }
      }.sequenceFu.void
    }

  def kickFromTour(
      tourId: Tournament.ID,
      userId: User.ID,
      by: User.ID,
  ): Funit =
    Sequencing(tourId)(tournamentRepo.enterableById) { tour =>
      (tour.createdBy == by) ?? {
        kickOrRemove(tour, userId) >> {
          if (tour.isStarted) {
            if (tour.isArena)
              pairingRepo.findPlaying(tour.id, userId).map {
                _ foreach { currentPairing =>
                  tellRound(currentPairing.gameId, AbortForce)
                }
              }
            else
              arrangementRepo.findPlaying(tour.id, userId) map { curArrangements =>
                curArrangements foreach { curArrangement =>
                  curArrangement.gameId foreach { tellRound(_, AbortForce) }
                }
              }
          } else updateNbPlayers(tour.id)
        } >>
          tournamentRepo.setDenied(tour.id, userId :: tour.denied) >>- {
            if (tour.hasArrangements) cached.arrangement.invalidatePlayers(tour.id)
            socket.foreach(_.reload(tour.id))
          }
      }
    }

  def ejectLameFromEnterable(tourId: Tournament.ID, userId: User.ID): Funit =
    Sequencing(tourId)(tournamentRepo.enterableById) { tour =>
      kickOrRemove(tour, userId) >> {
        if (tour.isStarted) {
          if (tour.isArena)
            pairingRepo.findPlaying(tour.id, userId).map {
              _ foreach { currentPairing =>
                tellRound(currentPairing.gameId, AbortForce)
              }
            } >> pairingRepo.opponentsOf(tour.id, userId).flatMap { uids =>
              pairingRepo.forfeitByTourAndUserId(tour.id, userId) >>
                lila.common.Future.applySequentially(uids.toList)(updateArenaPlayer(tour, none))
            }
          else
            arrangementRepo.findPlaying(tour.id, userId) map { curArrangements =>
              curArrangements foreach { curArrangement =>
                curArrangement.gameId foreach {
                  tellRound(_, AbortForce)
                }
              }
            }
        } else updateNbPlayers(tour.id)
      } >>- {
        if (tour.hasArrangements) cached.arrangement.invalidatePlayers(tour.id)
        socket.foreach(_.reload(tour.id))
      }
    }

  def ejectLameFromHistory(tourId: Tournament.ID, userId: User.ID): Funit =
    Sequencing(tourId)(tournamentRepo.finishedById) { tour =>
      kickOrRemove(tour, userId) >> {
        tour.winnerId.contains(userId) ?? {
          playerRepo winner tour.id flatMap {
            _ ?? { p =>
              tournamentRepo.setWinnerId(tour.id, p.userId)
            }
          }
        }
      }
    }

  private def kickOrRemove(tour: Tournament, userId: User.ID) =
    if (tour.isCreated) playerRepo.remove(tour.id, userId)
    else playerRepo.kick(tour.id, userId)

  private val tournamentTopNb = 20
  private val tournamentTopCache = cacheApi[Tournament.ID, TournamentTop](16, "tournament.top") {
    _.refreshAfterWrite(3 second)
      .expireAfterAccess(5 minutes)
      .maximumSize(64)
      .buildAsyncFuture { id =>
        playerRepo.bestByTour(id, tournamentTopNb) dmap TournamentTop.apply
      }
  }

  def tournamentTop(tourId: Tournament.ID): Fu[TournamentTop] =
    tournamentTopCache get tourId

  object gameView {

    def player(pov: Pov): Fu[Option[GameView]] =
      (pov.game.tournamentId ?? get) flatMap {
        _ ?? { tour =>
          getTeamVs(tour, pov.game) zip getGameRanks(tour, pov.game) flatMap {
            case (teamVs, ranks) =>
              teamVs.fold(tournamentTop(tour.id) dmap some) { vs =>
                cached.teamInfo.get(tour.id -> vs.teams(pov.color)) map2 { info =>
                  TournamentTop(info.topPlayers take tournamentTopNb)
                }
              } dmap {
                GameView(tour, teamVs, ranks, _).some
              }
          }
        }
      }

    def watcher(game: Game): Fu[Option[GameView]] =
      (game.tournamentId ?? get) flatMap {
        _ ?? { tour =>
          getTeamVs(tour, game) zip getGameRanks(tour, game) dmap { case (teamVs, ranks) =>
            GameView(tour, teamVs, ranks, none).some
          }
        }
      }

    def mobile(game: Game): Fu[Option[GameView]] =
      (game.tournamentId ?? get) flatMap {
        _ ?? { tour =>
          getGameRanks(tour, game) dmap { ranks =>
            GameView(tour, none, ranks, none).some
          }
        }
      }

    def analysis(game: Game): Fu[Option[GameView]] =
      (game.tournamentId ?? get) flatMap {
        _ ?? { tour =>
          getTeamVs(tour, game) dmap { GameView(tour, _, none, none).some }
        }
      }

    def withTeamVs(game: Game): Fu[Option[TourAndTeamVs]] =
      (game.tournamentId ?? get) flatMap {
        _ ?? { tour =>
          getTeamVs(tour, game) dmap { TourAndTeamVs(tour, _).some }
        }
      }

    private def getGameRanks(tour: Tournament, game: Game): Fu[Option[GameRanks]] =
      ~ {
        game.sentePlayer.userId.ifTrue(tour.isStarted) flatMap { senteId =>
          game.gotePlayer.userId map { goteId =>
            cached ranking tour map { ranking =>
              import cats.implicits._
              (ranking.get(senteId), ranking.get(goteId)) mapN { (senteR, goteR) =>
                GameRanks(senteR + 1, goteR + 1)
              }
            }
          }
        }
      }

    private def getTeamVs(tour: Tournament, game: Game): Fu[Option[TeamBattle.TeamVs]] =
      tour.isTeamBattle ?? playerRepo.teamVs(tour.id, game)
  }

  def notableFinished = cached.notableFinishedCache.get {}

  def startedScheduled = tournamentRepo.startedScheduled(8)

  def playerInfo(tour: Tournament, userId: User.ID): Fu[Option[PlayerInfoExt]] =
    playerRepo.find(tour.id, userId) flatMap {
      _ ?? { player =>
        playerPovs(tour, userId, 50) map { povs =>
          PlayerInfoExt(userId, player, povs).some
        }
      }
    }

  def calendar: Fu[List[Tournament]] = {
    val from = DateTime.now.minusDays(1)
    tournamentRepo.calendar(from = from, to = from plusYears 1)
  }

  def resultStream(tour: Tournament, perSecond: MaxPerSecond, nb: Int): Source[Player.Result, _] =
    playerRepo
      .sortedCursor(tour.id, perSecond.value)
      .documentSource(nb)
      .throttle(perSecond.value, 1 second)
      .zipWithIndex
      .mapAsync(8) { case (player, index) =>
        lightUserApi.async(player.userId) map { lu =>
          Player.Result(player, lu | LightUser.fallback(player.userId), index.toInt + 1)
        }
      }

  def byOwnerStream(owner: User, perSecond: MaxPerSecond, nb: Int): Source[Tournament, _] =
    tournamentRepo
      .sortedCursor(owner, perSecond.value)
      .documentSource(nb)
      .throttle(perSecond.value, 1 second)

  def byOwnerPager(owner: User, page: Int): Fu[Paginator[Tournament]] =
    Paginator(
      adapter = tournamentRepo.byOwnerAdapter(owner),
      currentPage = page,
      maxPerPage = MaxPerPage(20),
    )

  object upcomingByPlayerPager {
    private val max = 20
    private val cache =
      cacheApi[User.ID, lila.db.paginator.StaticAdapter[Tournament]](
        32,
        "tournament.upcomingByPlayer",
      ) {
        _.expireAfterWrite(30 seconds)
          .buildAsyncFuture {
            tournamentRepo.upcomingAdapterExpensiveCacheMe(_, max)
          }
      }
    def apply(player: User, page: Int): Fu[Paginator[Tournament]] =
      cache.get(player.id) flatMap { adapter =>
        Paginator(
          adapter = adapter,
          currentPage = page,
          maxPerPage = MaxPerPage(max),
        )
      }
  }

  def arrangemenstUpcoming(user: User, page: Int): Fu[Paginator[Arrangement]] =
    Paginator(
      adapter = arrangementRepo.upcomingAdapter(user),
      currentPage = page,
      maxPerPage = MaxPerPage(20),
    )

  def arrangemenstUpdated(user: User, page: Int): Fu[Paginator[Arrangement]] =
    Paginator(
      adapter = arrangementRepo.updatedAdapter(user),
      currentPage = page,
      maxPerPage = MaxPerPage(20),
    )

  def visibleByTeam(teamId: TeamID, nbPast: Int, nbNext: Int): Fu[Tournament.PastAndNext] =
    tournamentRepo.finishedByTeam(teamId, nbPast) zip
      tournamentRepo.upcomingByTeam(teamId, nbNext) map
      (Tournament.PastAndNext.apply _).tupled

  private def playerPovs(tour: Tournament, userId: User.ID, nb: Int): Fu[List[LightPov]] =
    (
      if (tour.isArena) pairingRepo.recentIdsByTourAndUserId(tour.id, userId, nb)
      else arrangementRepo.recentGameIdsByTourAndUserId(tour.id, userId, nb)
    ) flatMap
      gameRepo.light.gamesFromPrimary map {
        _ flatMap { LightPov.ofUserId(_, userId) }
      }

  private def Sequencing(
      tourId: Tournament.ID,
  )(fetch: Tournament.ID => Fu[Option[Tournament]])(run: Tournament => Funit): Funit =
    workQueue(tourId) {
      fetch(tourId) flatMap {
        _ ?? run
      }
    }

  def ArrangementUpdate(
      lookup: Arrangement.Lookup,
      userId: User.ID,
      filter: (Tournament, Arrangement) => Boolean,
  )(run: (Tournament, Arrangement) => Funit): Funit =
    Sequencing(lookup.tourId)(tournamentRepo.enterableById) { tour =>
      arrangementRepo.byLookup(lookup) flatMap { arrangementOpt =>
        arrangementOpt
          .fold(
            if (tour.isOrganized && tour.createdBy == userId)
              arrangementRepo.countByTour(tour.id) map { count =>
                (count < 500) ?? {
                  Arrangement.make(tour.id, lookup.users).some
                }
              }
            else if (tour.isRobin)
              playerRepo.findActiveTuple2(lookup.tourId, lookup.users) map { players =>
                players ?? { ps =>
                  val orderedUsers =
                    if (ps._1.order.zip(ps._2.order).exists { case (o1, o2) => o1 > o2 })
                      (ps._2.userId, ps._1.userId)
                    else (ps._1.userId, ps._2.userId)
                  Arrangement.make(tour.id, orderedUsers).some
                }
              }
            else fuccess(none),
          )(a => fuccess(a.some))
          .flatMap(aOpt => aOpt.filter(a => filter(tour, a)) ?? { a => run(tour, a) })
      }
    }

  private object updateTournamentStanding {

    import lila.hub.EarlyMultiThrottler

    // last published top hashCode
    private val lastPublished = lila.memo.CacheApi.scaffeineNoScheduler
      .initialCapacity(16)
      .expireAfterWrite(2 minute)
      .build[Tournament.ID, Int]()

    private def publishNow(tourId: Tournament.ID) =
      tournamentTop(tourId) map { top =>
        val lastHash: Int = ~lastPublished.getIfPresent(tourId)
        if (lastHash != top.hashCode) {
          Bus.publish(
            lila.hub.actorApi.round.TourStanding(tourId, JsonView.top(top, lightUserApi.sync)),
            "tourStanding",
          )
          lastPublished.put(tourId, top.hashCode)
        }
      }

    private val throttler = system.actorOf(Props(new EarlyMultiThrottler(logger = logger)))

    def apply(tour: Tournament): Unit =
      if (!tour.isTeamBattle)
        throttler ! EarlyMultiThrottler.Work(
          id = tour.id,
          run = () => publishNow(tour.id),
          cooldown = 15.seconds,
        )
  }

  // work around circular dependency
  private var socket: Option[TournamentSocket]                = None
  private[tournament] def registerSocket(s: TournamentSocket) = { socket = s.some }
}

private object TournamentApi {

  case class Callbacks(
      clearJsonViewCache: Tournament => Unit,
      clearWinnersCache: Tournament => Unit,
      clearTrophyCache: Tournament => Unit,
      indexLeaderboard: Tournament => Funit,
  )
}
