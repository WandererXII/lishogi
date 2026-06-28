package lila.study

import lila.common.paginator.Paginator
import lila.db.dsl._
import lila.db.paginator.Adapter
import lila.db.paginator.CachedAdapter
import lila.i18n.I18nKey
import lila.i18n.{ I18nKeys => trans }
import lila.user.User

final class StudyPager(
    studyRepo: StudyRepo,
    chapterRepo: ChapterRepo,
)(implicit ec: scala.concurrent.ExecutionContext) {

  val maxPerPage                = lila.common.config.MaxPerPage(16)
  val defaultNbChaptersPerStudy = 4

  import BSONHandlers._
  import StudyPager._
  import studyRepo.postGameStudy
  import studyRepo.selectLangByCode
  import studyRepo.selectLiker
  import studyRepo.selectMemberId
  import studyRepo.selectOwnerId
  import studyRepo.selectPrivateOrUnlisted
  import studyRepo.selectPublic
  import studyRepo.selectTopic

  def all(me: Option[User], langCode: String, order: Order, page: Int) =
    paginator(
      postGameStudy(false) ++ accessSelect(me),
      me,
      langCode,
      order,
      page,
      fuccess(9999).some,
    )

  def byOwner(owner: User, me: Option[User], langCode: String, order: Order, page: Int) =
    paginator(
      postGameStudy(false) ++ selectOwnerId(owner.id) ++ accessSelect(me),
      me,
      langCode,
      order,
      page,
    )

  def mine(me: User, langCode: String, order: Order, page: Int) =
    paginator(
      postGameStudy(false) ++ selectOwnerId(me.id),
      me.some,
      langCode,
      order,
      page,
    )

  def minePublic(me: User, langCode: String, order: Order, page: Int) =
    paginator(
      postGameStudy(false) ++ selectOwnerId(me.id) ++ selectPublic,
      me.some,
      langCode,
      order,
      page,
    )

  def minePrivate(me: User, langCode: String, order: Order, page: Int) =
    paginator(
      postGameStudy(false) ++ selectOwnerId(me.id) ++ selectPrivateOrUnlisted,
      me.some,
      langCode,
      order,
      page,
    )

  def mineMember(me: User, langCode: String, order: Order, page: Int) =
    paginator(
      postGameStudy(false) ++ selectMemberId(me.id) ++ $doc("ownerId" $ne me.id),
      me.some,
      langCode,
      order,
      page,
    )

  def mineLikes(me: User, langCode: String, order: Order, page: Int) =
    paginator(
      postGameStudy(false) ++ selectLiker(me.id) ++ accessSelect(me.some) ++ $doc(
        "ownerId" $ne me.id,
      ),
      me.some,
      langCode,
      order,
      page,
    )

  def minePostGameStudies(me: User, langCode: String, order: Order, page: Int) =
    paginator(
      postGameStudy(true) ++ selectMemberId(me.id),
      me.some,
      langCode,
      order,
      page,
    )

  def postGameStudiesOf(
      gameId: String,
      me: Option[User],
      langCode: String,
      order: Order,
      page: Int,
  ) =
    paginator(
      postGameStudy(true) ++ $doc("postGameStudy.gameId" $eq s"$gameId"),
      me,
      langCode,
      order,
      page,
    )

  def byTopic(topic: StudyTopic, me: Option[User], langCode: String, order: Order, page: Int) = {
    val onlyMine = me.ifTrue(order == Order.Mine)
    paginator(
      selectTopic(topic) ++ onlyMine.fold(accessSelect(me))(m => selectMemberId(m.id)),
      me,
      langCode,
      order,
      page,
      hint = onlyMine.isDefined option $doc("uids" -> 1, "rank" -> -1),
    )
  }

  private def accessSelect(me: Option[User]) =
    me.fold(selectPublic) { u =>
      $or(selectPublic, selectMemberId(u.id))
    }

  private def paginator(
      selector: Bdoc,
      me: Option[User],
      langCode: String,
      order: Order,
      page: Int,
      nbResults: Option[Fu[Int]] = none,
      hint: Option[Bdoc] = none,
  ): Fu[Paginator[Study.WithChaptersAndLiked]] = studyRepo.coll { coll =>
    val adapter = new Adapter[Study](
      collection = coll,
      selector = selector ++ selectLangByCode(StudyPager.Lang.filterLangCode(langCode)),
      projection = studyRepo.projection.some,
      sort = order match {
        case Order.Hot     => $sort desc "rank"
        case Order.Newest  => $sort desc "createdAt"
        case Order.Oldest  => $sort asc "createdAt"
        case Order.Updated => $sort desc "updatedAt"
        case Order.Popular => $sort desc "likes"
        // mine filter for topic view
        case Order.Mine => $sort desc "rank"
      },
      hint = hint,
    ) mapFutureList withChaptersAndLiking(me)
    Paginator(
      adapter = nbResults.fold(adapter) { nb =>
        new CachedAdapter(adapter, nb)
      },
      currentPage = page,
      maxPerPage = maxPerPage,
    )
  }

  def withChaptersAndLiking(
      me: Option[User],
      nbChaptersPerStudy: Int = defaultNbChaptersPerStudy,
  )(studies: Seq[Study]): Fu[Seq[Study.WithChaptersAndLiked]] =
    withChapters(studies, nbChaptersPerStudy) flatMap withLiking(me)

  private def withChapters(
      studies: Seq[Study],
      nbChaptersPerStudy: Int,
  ): Fu[Seq[Study.WithChapters]] =
    chapterRepo.idNamesByStudyIds(studies.map(_.id), nbChaptersPerStudy) map { chapters =>
      studies.map { study =>
        Study.WithChapters(study, (chapters get study.id) ?? (_ map (_.name)))
      }
    }

  private def withLiking(
      me: Option[User],
  )(studies: Seq[Study.WithChapters]): Fu[Seq[Study.WithChaptersAndLiked]] =
    me.?? { u =>
      studyRepo.filterLiked(u, studies.map(_.study.id))
    } map { liked =>
      studies.map { case Study.WithChapters(study, chapters) =>
        Study.WithChaptersAndLiked(study, chapters, liked(study.id))
      }
    }
}

object StudyPager {

  object Lang {
    val default = "all"

    def filterLangCode(langCode: String): Option[String] =
      langCode.some.filter(l => l != default)

  }

  sealed abstract class Order(val key: String, val name: I18nKey)

  object Order {
    case object Hot     extends Order("hot", trans.study.hot)
    case object Newest  extends Order("newest", trans.study.dateAddedNewest)
    case object Oldest  extends Order("oldest", trans.study.dateAddedOldest)
    case object Updated extends Order("updated", trans.study.recentlyUpdated)
    case object Popular extends Order("popular", trans.study.mostPopular)
    case object Mine    extends Order("mine", trans.study.myStudies)

    val default      = Hot
    val all          = List(Hot, Newest, Oldest, Updated, Popular)
    val allButOldest = all filter (Oldest !=)
    val allWithMine  = Mine :: all
    private val byKey: Map[String, Order] = allWithMine.map { o =>
      o.key -> o
    }.toMap
    def apply(key: String): Order = byKey.getOrElse(key, default)
  }
}
