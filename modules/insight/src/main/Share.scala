package lishogi.insight

import lishogi.pref.Pref
import lishogi.security.Granter
import lishogi.user.User

final class Share(
    prefApi: lishogi.pref.PrefApi,
    relationApi: lishogi.relation.RelationApi
)(implicit ec: scala.concurrent.ExecutionContext) {

  def getPrefId(insighted: User) = prefApi.getPrefById(insighted.id) dmap (_.insightShare)

  def grant(insighted: User, to: Option[User]): Fu[Boolean] =
    if (to ?? Granter(_.SeeInsight)) fuTrue
    else
      prefApi.getPrefById(insighted.id) flatMap { pref =>
        pref.insightShare match {
          case _ if to.contains(insighted) => fuTrue
          case Pref.InsightShare.EVERYBODY => fuTrue
          case Pref.InsightShare.FRIENDS =>
            to ?? { t =>
              relationApi.fetchAreFriends(insighted.id, t.id)
            }
          case Pref.InsightShare.NOBODY => fuFalse
        }
      }
}
