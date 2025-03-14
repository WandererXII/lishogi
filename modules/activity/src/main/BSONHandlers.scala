package lila.activity

import scala.util.Success

import reactivemongo.api.bson._

import lila.common.Day
import lila.common.Iso
import lila.db.dsl._
import lila.rating.BSONHandlers.perfTypeKeyIso
import lila.rating.PerfType
import lila.study.BSONHandlers._
import lila.study.Study
import lila.user.User

private object BSONHandlers {

  import Activity._
  import activities._
  import model._

  def regexId(userId: User.ID): Bdoc = "_id" $startsWith s"$userId:"

  implicit lazy val activityIdHandler: BSONHandler[Id] = {
    val sep = ':'
    tryHandler[Id](
      { case BSONString(v) =>
        v split sep match {
          case Array(userId, dayStr) => Success(Id(userId, Day(Integer.parseInt(dayStr))))
          case _                     => handlerBadValue(s"Invalid activity id $v")
        }
      },
      id => BSONString(s"${id.userId}$sep${id.day.value}"),
    )
  }

  implicit private lazy val ratingHandler: BSONHandler[Rating] =
    BSONIntegerHandler.as[Rating](Rating.apply, _.value)
  implicit private lazy val ratingProgHandler: BSONHandler[RatingProg] = tryHandler[RatingProg](
    { case v: BSONArray =>
      for {
        before <- v.getAsTry[Rating](0)
        after  <- v.getAsTry[Rating](1)
      } yield RatingProg(before, after)
    },
    o => BSONArray(o.before, o.after),
  )

  implicit private lazy val scoreHandler: lila.db.BSON[Score] = new lila.db.BSON[Score] {
    private val win  = "w"
    private val loss = "l"
    private val draw = "d"
    private val rp   = "r"

    def reads(r: lila.db.BSON.Reader) =
      Score(
        win = r.intD(win),
        loss = r.intD(loss),
        draw = r.intD(draw),
        rp = r.getO[RatingProg](rp),
      )

    def writes(w: lila.db.BSON.Writer, o: Score) =
      BSONDocument(
        win  -> w.intO(o.win),
        loss -> w.intO(o.loss),
        draw -> w.intO(o.draw),
        rp   -> o.rp,
      )
  }

  implicit lazy val gamesHandler: BSONHandler[Games] =
    typedMapHandler[PerfType, Score](perfTypeKeyIso)
      .as[Games](Games.apply, _.value)

  implicit private lazy val gameIdHandler: BSONHandler[GameId] =
    BSONStringHandler.as[GameId](GameId.apply, _.value)

  implicit private lazy val postIdHandler: BSONHandler[PostId] =
    BSONStringHandler.as[PostId](PostId.apply, _.value)
  implicit lazy val postsHandler: BSONHandler[Posts] =
    isoHandler[Posts, List[PostId]]((p: Posts) => p.value, Posts.apply _)

  implicit lazy val puzzlesHandler: BSONHandler[Puzzles] =
    isoHandler[Puzzles, Score]((p: Puzzles) => p.score, Puzzles.apply _)

  implicit lazy val stormHandler: lila.db.BSON[Storm] = new lila.db.BSON[Storm] {
    def reads(r: lila.db.BSON.Reader)            = Storm(r.intD("r"), r.intD("s"))
    def writes(w: lila.db.BSON.Writer, s: Storm) = BSONDocument("r" -> s.runs, "s" -> s.score)
  }

  implicit private lazy val practiceHandler: BSONHandler[Practice] =
    typedMapHandler[Study.Id, Int](Iso.string[Study.Id](Study.Id.apply, _.value))
      .as[Practice](Practice.apply, _.value)

  implicit private lazy val simulIdHandler: BSONHandler[SimulId] =
    BSONStringHandler.as[SimulId](SimulId.apply, _.value)
  implicit private lazy val simulsHandler: BSONHandler[Simuls] =
    isoHandler[Simuls, List[SimulId]]((s: Simuls) => s.value, Simuls.apply _)

  implicit lazy val corresHandler: BSONDocumentHandler[Corres] = Macros.handler[Corres]
  implicit private lazy val patronHandler: BSONHandler[Patron] =
    BSONIntegerHandler.as[Patron](Patron.apply, _.months)

  implicit private lazy val followListHandler: BSONDocumentHandler[FollowList] =
    Macros.handler[FollowList]

  implicit private lazy val followsHandler: lila.db.BSON[Follows] = new lila.db.BSON[Follows] {
    def reads(r: lila.db.BSON.Reader) =
      Follows(
        in = r.getO[FollowList]("i").filterNot(_.isEmpty),
        out = r.getO[FollowList]("o").filterNot(_.isEmpty),
      )
    def writes(w: lila.db.BSON.Writer, o: Follows) =
      BSONDocument(
        "i" -> o.in,
        "o" -> o.out,
      )
  }

  implicit private lazy val studiesHandler: BSONHandler[Studies] =
    isoHandler[Studies, List[Study.Id]]((s: Studies) => s.value, Studies.apply _)
  implicit private lazy val teamsHandler: BSONHandler[Teams] =
    isoHandler[Teams, List[String]]((s: Teams) => s.value, Teams.apply _)

  object ActivityFields {
    val id       = "_id"
    val games    = "g"
    val posts    = "p"
    val puzzles  = "z"
    val storm    = "m"
    val practice = "r"
    val simuls   = "s"
    val corres   = "o"
    val patron   = "a"
    val follows  = "f"
    val studies  = "t"
    val teams    = "e"
    val stream   = "st"
  }

  implicit lazy val activityHandler: lila.db.BSON[Activity] = new lila.db.BSON[Activity] {

    import ActivityFields._

    def reads(r: lila.db.BSON.Reader) =
      Activity(
        id = r.get[Id](id),
        games = r.getO[Games](games),
        posts = r.getO[Posts](posts),
        puzzles = r.getO[Puzzles](puzzles),
        storm = r.getO[Storm](storm),
        practice = r.getO[Practice](practice),
        simuls = r.getO[Simuls](simuls),
        corres = r.getO[Corres](corres),
        patron = r.getO[Patron](patron),
        follows = r.getO[Follows](follows).filterNot(_.isEmpty),
        studies = r.getO[Studies](studies),
        teams = r.getO[Teams](teams),
        stream = r.getD[Boolean](stream),
      )

    def writes(w: lila.db.BSON.Writer, o: Activity) =
      BSONDocument(
        id       -> o.id,
        games    -> o.games,
        posts    -> o.posts,
        puzzles  -> o.puzzles,
        storm    -> o.storm,
        practice -> o.practice,
        simuls   -> o.simuls,
        corres   -> o.corres,
        patron   -> o.patron,
        follows  -> o.follows,
        studies  -> o.studies,
        teams    -> o.teams,
        stream   -> o.stream.option(true),
      )
  }
}
