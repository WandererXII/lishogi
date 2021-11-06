package lishogi.api

import play.api.i18n.Lang
import play.api.libs.json._

import shogi.format.Forsyth
import lishogi.common.Json.jodaWrites
import lishogi.common.LightUser
import lishogi.common.paginator.Paginator
import lishogi.game.{ Game, PerfPicker }
import lishogi.user.User

final class UserGameApi(
    bookmarkApi: lishogi.bookmark.BookmarkApi,
    lightUser: lishogi.user.LightUserApi,
    getTournamentName: lishogi.tournament.GetTourName
)(implicit ec: scala.concurrent.ExecutionContext) {

  import lishogi.game.JsonView._
  import LightUser.lightUserWrites

  def jsPaginator(pag: Paginator[Game])(implicit ctx: Context): Fu[JsObject] =
    for {
      bookmarkedIds <- bookmarkApi.filterGameIdsBookmarkedBy(pag.currentPageResults, ctx.me)
      _             <- lightUser.preloadMany(pag.currentPageResults.flatMap(_.userIds))
    } yield {
      implicit val gameWriter = Writes[Game] { g =>
        write(g, bookmarkedIds(g.id), ctx.me)(ctx.lang)
      }
      Json.obj(
        "paginator" -> lishogi.common.paginator.PaginatorJson(pag)
      )
    }

  private def write(g: Game, bookmarked: Boolean, as: Option[User])(implicit lang: Lang) =
    Json
      .obj(
        "id"        -> g.id,
        "rated"     -> g.rated,
        "variant"   -> g.variant,
        "speed"     -> g.speed.key,
        "perf"      -> PerfPicker.key(g),
        "timestamp" -> g.createdAt,
        "turns"     -> g.turns,
        "status"    -> g.status,
        "source"    -> g.source.map(_.name),
        "players" -> JsObject(g.players map { p =>
          p.color.name -> Json
            .obj(
              "user"   -> p.userId.flatMap(lightUser.sync),
              "userId" -> p.userId, // for BC
              "name"   -> p.name
            )
            .add("id" -> as.exists(p.isUser).option(p.id))
            .add("aiLevel" -> p.aiLevel)
            .add("rating" -> p.rating)
            .add("ratingDiff" -> p.ratingDiff)
        }),
        "fen"       -> Forsyth.exportSituation(g.situation),
        "winner"    -> g.winnerColor.map(_.name),
        "bookmarks" -> g.bookmarks
      )
      .add("bookmarked" -> bookmarked)
      .add("analysed" -> g.metadata.analysed)
      .add("opening" -> g.opening)
      .add("lastMove" -> g.lastMoveKeys)
      .add("clock" -> g.clock)
      .add("correspondence" -> g.daysPerTurn.map { d =>
        Json.obj("daysPerTurn" -> d)
      })
      .add("tournament" -> g.tournamentId.map { tid =>
        Json.obj("id" -> tid, "name" -> getTournamentName.get(tid))
      })
}
