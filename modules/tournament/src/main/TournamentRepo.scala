package lila.tournament

import org.joda.time.DateTime
import reactivemongo.akkastream.AkkaStreamCursor
import reactivemongo.akkastream.cursorProducer
import reactivemongo.api.ReadPreference

import lila.common.config.CollName
import lila.db.dsl._
import lila.game.Game
import lila.hub.LightTeam.TeamID
import lila.tournament.BSONHandlers._
import lila.user.User

final class TournamentRepo(val coll: Coll, playerCollName: CollName)(implicit
    ec: scala.concurrent.ExecutionContext,
) {

  private val enterableSelect             = $doc("status" $lt Status.Finished.id)
  private[tournament] val createdSelect   = $doc("status" -> Status.Created.id)
  private val startedSelect               = $doc("status" -> Status.Started.id)
  private[tournament] val finishedSelect  = $doc("status" -> Status.Finished.id)
  private val unfinishedSelect            = $doc("status" $ne Status.Finished.id)
  private[tournament] val scheduledSelect = $doc("schedule" $exists true)
  private def forTeamSelect(id: TeamID)   = $doc("forTeams" -> id)
  private[tournament] val nonEmptySelect  = $doc("nbPlayers" $ne 0)
  private[tournament] val selectUnique    = $doc("schedule.freq" -> "unique")
  private val arena                       = $doc("format" $exists false)
  // private val robin                    = $doc("format" -> Format.Robin.key)

  def byId(id: Tournament.ID): Fu[Option[Tournament]] = coll.byId[Tournament](id)

  def byIds(ids: Iterable[Tournament.ID]): Fu[List[Tournament]] =
    coll.list[Tournament]($inIds(ids))

  def byOrderedIds(ids: Iterable[Tournament.ID]): Fu[List[Tournament]] =
    coll.byOrderedIds[Tournament, Tournament.ID](
      ids,
      readPreference = ReadPreference.secondaryPreferred,
    )(
      _.id,
    )

  def arenaById(id: Tournament.ID): Fu[Option[Tournament]] =
    coll.one[Tournament]($id(id) ++ arena)

  def uniqueById(id: Tournament.ID): Fu[Option[Tournament]] =
    coll.one[Tournament]($id(id) ++ selectUnique)

  def createdById(id: Tournament.ID): Fu[Option[Tournament]] =
    coll.one[Tournament]($id(id) ++ createdSelect)

  def enterableById(id: Tournament.ID): Fu[Option[Tournament]] =
    coll.one[Tournament]($id(id) ++ enterableSelect)

  def startedById(id: Tournament.ID): Fu[Option[Tournament]] =
    coll.one[Tournament]($id(id) ++ startedSelect)

  def finishedById(id: Tournament.ID): Fu[Option[Tournament]] =
    coll.one[Tournament]($id(id) ++ finishedSelect)

  def countCreated: Fu[Int] = coll.countSel(createdSelect)

  private[tournament] def startedArenaCursor =
    coll
      .find(startedSelect ++ arena)
      .sort($doc("createdAt" -> -1))
      .batchSize(1)
      .cursor[Tournament]()

  private[tournament] def notableFinished(limit: Int): Fu[List[Tournament]] =
    coll
      .find(finishedSelect)
      .sort($sort desc "startsAt")
      .cursor[Tournament]()
      .list(limit)

  private[tournament] def byOwnerAdapter(owner: User) =
    new lila.db.paginator.Adapter[Tournament](
      collection = coll,
      selector = $doc("createdBy" -> owner.id),
      projection = none,
      sort = $sort desc "startsAt",
      readPreference = ReadPreference.secondaryPreferred,
    )

  private[tournament] def upcomingAdapterExpensiveCacheMe(userId: User.ID, max: Int) =
    coll
      .aggregateList(max, readPreference = ReadPreference.secondaryPreferred) {
        implicit framework =>
          import framework._
          Match(enterableSelect ++ nonEmptySelect) -> List(
            PipelineOperator(
              $doc(
                "$lookup" -> $doc(
                  "from" -> playerCollName.value,
                  "let"  -> $doc("tid" -> "$_id"),
                  "pipeline" -> $arr(
                    $doc(
                      "$match" -> $doc(
                        "$expr" -> $doc(
                          "$and" -> $arr(
                            $doc("$eq" -> $arr("$uid", userId)),
                            $doc("$eq" -> $arr("$tid", "$$tid")),
                          ),
                        ),
                      ),
                    ),
                    $doc("$project" -> $doc("tid" -> true, "_id" -> false)),
                  ),
                  "as" -> "player",
                ),
              ),
            ),
            Match("player" $ne $arr()),
            Limit(max),
          )
      }
      .map(_.flatMap(_.asOpt[Tournament]))
      .dmap { new lila.db.paginator.StaticAdapter(_) }

  def isUnfinished(tourId: Tournament.ID): Fu[Boolean] =
    coll.exists($id(tourId) ++ unfinishedSelect)

  def isCreator(tourId: Tournament.ID, userId: User.ID) =
    coll.exists($id(tourId) ++ $doc("createdBy" -> userId))

  def byTeamCursor(teamId: TeamID) =
    coll
      .find(forTeamSelect(teamId))
      .sort($sort desc "startsAt")
      .cursor[Tournament]()

  private[tournament] def upcomingByTeam(teamId: TeamID, nb: Int) =
    (nb > 0) ?? coll
      .find(
        forTeamSelect(teamId) ++ enterableSelect ++ $doc(
          "startsAt" $gt DateTime.now.minusDays(1),
        ),
      )
      .sort($sort asc "startsAt")
      .cursor[Tournament]()
      .list(nb)

  private[tournament] def finishedByTeam(teamId: TeamID, nb: Int) =
    (nb > 0) ?? coll
      .find(forTeamSelect(teamId) ++ finishedSelect)
      .sort($sort desc "startsAt")
      .cursor[Tournament]()
      .list(nb)

  private[tournament] def setForTeam(tourId: Tournament.ID, teamId: TeamID) =
    coll.update.one($id(tourId), $addToSet("forTeams" -> teamId))

  private[tournament] def withdrawableIds(
      userId: User.ID,
      teamId: Option[TeamID] = None,
      onlyArena: Boolean = false,
  ): Fu[List[Tournament.ID]] =
    coll
      .aggregateList(Int.MaxValue, readPreference = ReadPreference.secondaryPreferred) {
        implicit framework =>
          import framework._
          Match(
            enterableSelect ++ nonEmptySelect ++ teamId.??(forTeamSelect) ++ onlyArena.??(arena),
          ) -> List(
            PipelineOperator(
              $doc(
                "$lookup" -> $doc(
                  "from" -> playerCollName.value,
                  "let"  -> $doc("t" -> "$_id"),
                  "pipeline" -> $arr(
                    $doc(
                      "$match" -> $doc(
                        "$expr" -> $doc(
                          "$and" -> $arr(
                            $doc("$eq" -> $arr("$uid", userId)),
                            $doc("$eq" -> $arr("$tid", "$$t")),
                          ),
                        ),
                      ),
                    ),
                  ),
                  "as" -> "player",
                ),
              ),
            ),
            Match("player" $ne $arr()),
            Project($id(true)),
          )
      }
      .map(_.flatMap(_.string("_id")))

  def setStatus(tourId: Tournament.ID, status: Status) =
    coll.updateField($id(tourId), "status", status.id).void

  def setNbPlayers(tourId: Tournament.ID, nb: Int) =
    coll.updateField($id(tourId), "nbPlayers", nb).void

  def setClosed(tourId: Tournament.ID, closed: Boolean) =
    coll.updateField($id(tourId), "closed", closed).void

  def setCandidates(tourId: Tournament.ID, candidates: List[User.ID]) =
    coll.updateField($id(tourId), "candidates", candidates).void

  def clearCandidates(tourId: Tournament.ID) =
    coll.update
      .one(
        $id(tourId),
        $unset(
          "candidates",
        ),
      )
      .void

  def setDenied(tourId: Tournament.ID, denied: List[User.ID]) =
    coll.updateField($id(tourId), "denied", denied).void

  def setProcessedCandidate(
      tourId: Tournament.ID,
      candidates: List[User.ID],
      denied: List[User.ID],
  ) =
    coll.update
      .one(
        $id(tourId),
        $set(
          "candidates" -> candidates,
          "denied"     -> denied,
        ),
      )
      .void

  def setWinnerId(tourId: Tournament.ID, userId: User.ID) =
    coll.updateField($id(tourId), "winner", userId).void

  def setFeaturedGameId(tourId: Tournament.ID, gameId: Game.ID) =
    coll.updateField($id(tourId), "featured", gameId).void

  def setTeamBattle(tourId: Tournament.ID, battle: TeamBattle) =
    coll.updateField($id(tourId), "teamBattle", battle).void

  def setReminderNotified(tourId: Tournament.ID, date: DateTime) =
    coll.updateField($id(tourId), "notified", date).void

  def teamBattleOf(tourId: Tournament.ID): Fu[Option[TeamBattle]] =
    coll.primitiveOne[TeamBattle]($id(tourId), "teamBattle")

  def isTeamBattle(tourId: Tournament.ID): Fu[Boolean] =
    coll.exists($id(tourId) ++ $doc("teamBattle" $exists true))

  private def startingSoonSelect(aheadMinutes: Int) =
    createdSelect ++
      $doc("startsAt" $lt (DateTime.now plusMinutes aheadMinutes))

  def enterable: Fu[List[Tournament]] =
    coll.list[Tournament](enterableSelect)

  def created(aheadMinutes: Int, limit: Int = Int.MaxValue): Fu[List[Tournament]] =
    coll.list[Tournament](startingSoonSelect(aheadMinutes), limit)

  def started: Fu[List[Tournament]] =
    coll.list[Tournament](startedSelect)

  def startedScheduled(limit: Int = Int.MaxValue): Fu[List[Tournament]] =
    coll.list[Tournament](startedSelect ++ scheduledSelect, limit)

  private[tournament] def shouldStartCursor =
    coll
      .find($doc("startsAt" $lt DateTime.now) ++ createdSelect)
      .batchSize(1)
      .cursor[Tournament]()

  private[tournament] def shouldEndNonArenaCursor =
    coll
      .find($doc("endsAt" $lt DateTime.now) ++ startedSelect) // endsAt set only for non arenas
      .batchSize(1)
      .cursor[Tournament]()

  def uniques(max: Int): Fu[List[Tournament]] =
    coll
      .find(selectUnique)
      .sort($doc("startsAt" -> -1))
      .hint(coll hint $doc("startsAt" -> -1))
      .cursor[Tournament]()
      .list(max)

  def scheduledUnfinished: Fu[List[Tournament]] =
    coll
      .find(scheduledSelect ++ unfinishedSelect)
      .sort($doc("startsAt" -> 1))
      .cursor[Tournament]()
      .list()

  def update(tour: Tournament) =
    coll.update.one(
      $id(tour.id),
      $set(tournamentHandler.write(tour)) ++ $unset(
        List(
          // tour.conditions.titled.isEmpty option "conditions.titled",
          tour.isRated option "mode",
          tour.berserkable option "noBerserk",
          tour.streakable option "noStreak",
          tour.hasChat option "chat",
          tour.password.isEmpty option "password",
          tour.conditions.list.isEmpty option "conditions",
          tour.maxPlayers.isEmpty option "maxPlayers",
          tour.position.isEmpty option "sfen",
          tour.variant.standard option "variant",
          tour.candidates.isEmpty option "candidates",
          !tour.closed option "closed",
          tour.denied.isEmpty option "denied",
          !tour.candidatesOnly option "candidatesOnly",
          !tour.proMode option "proMode",
          tour.icon.isEmpty option "icon",
        ).flatten,
      ),
    )

  def insert(tour: Tournament) = coll.insert.one(tour)

  def insert(tours: Seq[Tournament]) = tours.nonEmpty ??
    coll.insert(ordered = false).many(tours).void

  def remove(tour: Tournament) = coll.delete.one($id(tour.id))

  def exists(id: Tournament.ID) = coll exists $id(id)

  def calendar(from: DateTime, to: DateTime): Fu[List[Tournament]] =
    coll
      .find(
        $doc("startsAt" $gte from $lte to) ++
          $or(
            "schedule.freq" $in Schedule.Freq.all.filter(_.isWeeklyOrBetter),
            "nbPlayers" $gte 5,
          ),
      )
      .sort($sort asc "startsAt")
      .cursor[Tournament](ReadPreference.secondaryPreferred)
      .list(128)

  private[tournament] def sortedCursor(
      owner: lila.user.User,
      batchSize: Int,
      readPreference: ReadPreference = ReadPreference.secondaryPreferred,
  ): AkkaStreamCursor[Tournament] =
    coll
      .find($doc("createdBy" -> owner.id))
      .sort($sort desc "startsAt")
      .batchSize(batchSize)
      .cursor[Tournament](readPreference)
}
