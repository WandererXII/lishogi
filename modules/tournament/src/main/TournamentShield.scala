package lila.tournament

import scala.concurrent.duration._

import play.api.i18n.Lang

import org.joda.time.DateTime
import reactivemongo.api.ReadPreference

import lila.common.Icons
import lila.db.dsl._
import lila.memo.CacheApi._
import lila.rating.PerfType
import lila.user.User

final class TournamentShieldApi(
    tournamentRepo: TournamentRepo,
    cacheApi: lila.memo.CacheApi,
)(implicit ec: scala.concurrent.ExecutionContext) {

  import BSONHandlers._
  import TournamentShield._

  def active(u: User): Fu[List[Award]] =
    cache.getUnit dmap {
      _.value.values.flatMap(_.headOption.filter(_.owner.value == u.id)).toList
    }

  def history(maxPerCateg: Option[Int]): Fu[History] =
    cache.getUnit dmap { h =>
      maxPerCateg.fold(h)(h.take)
    }

  def byCategKey(k: String): Fu[Option[(Category, List[Award])]] =
    Category.byKey(k) ?? { categ =>
      cache.getUnit dmap {
        _.value get categ map {
          categ -> _
        }
      }
    }

  def currentOwner(tour: Tournament): Fu[Option[OwnerId]] =
    tour.isShield ?? {
      Category.of(tour) ?? { cat =>
        history(none).map(_.current(cat).map(_.owner))
      }
    }

  private[tournament] def clear(): Unit = cache.invalidateUnit().unit

  private val cache = cacheApi.unit[History] {
    _.refreshAfterWrite(1 day)
      .buildAsyncFuture { _ =>
        tournamentRepo.coll
          .find(
            $doc(
              "schedule.freq" -> scheduleFreqHandler.writeTry(Schedule.Freq.Shield).get,
              "status"        -> statusBSONHandler.writeTry(Status.Finished).get,
            ),
          )
          .sort($sort asc "startsAt")
          .cursor[Tournament](ReadPreference.secondaryPreferred)
          .list() map { tours =>
          for {
            tour   <- tours
            categ  <- Category of tour
            winner <- tour.winnerId
          } yield Award(
            categ = categ,
            owner = OwnerId(winner),
            date = tour.finishesAt,
            tourId = tour.id,
          )
        } map {
          _.foldLeft(Map.empty[Category, List[Award]]) { case (hist, entry) =>
            hist + (entry.categ -> hist.get(entry.categ).fold(List(entry))(entry :: _))
          }
        } dmap History.apply
      }
  }
}

object TournamentShield {

  case class OwnerId(value: String) extends AnyVal

  case class Award(
      categ: Category,
      owner: OwnerId,
      date: DateTime,
      tourId: Tournament.ID,
  )
  // newer entry first
  case class History(value: Map[Category, List[Award]]) {

    def sorted: List[(Category, List[Award])] =
      Category.all map { categ =>
        categ -> ~(value get categ)
      }

    def userIds: List[User.ID] = value.values.flatMap(_.map(_.owner.value)).toList

    def current(cat: Category): Option[Award] = value get cat flatMap (_.headOption)

    def take(max: Int) =
      copy(
        value = value.view.mapValues(_ take max).toMap,
      )
  }

  private type SpeedOrVariant = Either[Schedule.Speed, shogi.variant.Variant]

  sealed abstract class Category(
      val of: SpeedOrVariant,
      val icon: String,
  ) {
    def key = of.fold(_.key, _.key)
    def matches(tour: Tournament) =
      if (tour.variant.standard) ~(for {
        tourSpeed  <- tour.schedule.map(_.speed)
        categSpeed <- of.left.toOption
      } yield tourSpeed == categSpeed)
      else of.toOption.has(tour.variant)
    def trans(implicit lang: Lang) = of.fold(
      s => Schedule.Speed.standardTrans(s),
      v => PerfType.byVariant(v).map(_.trans).getOrElse(key),
    )
  }

  object Category {

    case object VeryFastShogi
        extends Category(
          of = Left(Schedule.Speed.VeryFast),
          icon = Icons.standard,
        )

    case object FastShogi
        extends Category(
          of = Left(Schedule.Speed.Fast),
          icon = Icons.standard,
        )

    case object Shogi
        extends Category(
          of = Left(Schedule.Speed.Normal),
          icon = Icons.standard,
        )

    case object Minishogi
        extends Category(
          of = Right(shogi.variant.Minishogi),
          icon = Icons.minishogi,
        )

    case object Annanshogi
        extends Category(
          of = Right(shogi.variant.Annanshogi),
          icon = Icons.annanshogi,
        )

    case object Kyotoshogi
        extends Category(
          of = Right(shogi.variant.Kyotoshogi),
          icon = Icons.kyotoshogi,
        )

    case object Checkshogi
        extends Category(
          of = Right(shogi.variant.Checkshogi),
          icon = Icons.checkshogi,
        )

    case object Dobutsu
        extends Category(
          of = Right(shogi.variant.Dobutsu),
          icon = Icons.dobutsu,
        )

    val all: List[Category] = List(
      VeryFastShogi,
      FastShogi,
      Shogi,
      Minishogi,
      Annanshogi,
      Kyotoshogi,
      Checkshogi,
      Dobutsu,
    )

    def of(t: Tournament): Option[Category] = all.find(_ matches t)

    def byKey(k: String): Option[Category] = all.find(_.key == k)
  }

}
