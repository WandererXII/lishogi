package views.html.user.show

import lishogi.api.Context
import lishogi.app.templating.Environment._
import lishogi.app.ui.ScalatagsTemplate._
import lishogi.rating.PerfType
import lishogi.user.User

import play.api.i18n.Lang

import controllers.routes

object side {

  def apply(
      u: User,
      rankMap: lishogi.rating.UserRankMap,
      active: Option[lishogi.rating.PerfType]
  )(implicit ctx: Context) = {

    def showNonEmptyPerf(perf: lishogi.rating.Perf, perfType: PerfType) =
      perf.nonEmpty option showPerf(perf, perfType)

    def showPerf(perf: lishogi.rating.Perf, perfType: PerfType) = {
      val isPuzzle = perfType == lishogi.rating.PerfType.Puzzle
      a(
        dataIcon := perfType.iconChar,
        title := perfType.desc,
        cls := List(
          "empty"  -> perf.isEmpty,
          "active" -> active.has(perfType)
        ),
        href := {
          if (isPuzzle) ctx.is(u) option routes.Puzzle.dashboard(30, "home").url
          else routes.User.perfStat(u.username, perfType.key).url.some
        },
        span(
          h3(perfType.trans),
          st.rating(
            strong(
              perf.glicko.intRating,
              perf.provisional option "?"
            ),
            " ",
            ratingProgress(perf.progress),
            " ",
            span(
              if (perfType.key == "puzzle") trans.nbPuzzles(perf.nb, perf.nb.localize)
              else trans.nbGames(perf.nb, perf.nb.localize)
            )
          ),
          rankMap get perfType map { rank =>
            span(cls := "rank", title := trans.rankIsUpdatedEveryNbMinutes.pluralSameTxt(15))(
              trans.rankX(rank.localize)
            )
          }
        ),
        !isPuzzle option iconTag("G")
      )
    }

    div(cls := "side sub-ratings")(
      (!u.lame || ctx.is(u) || isGranted(_.UserSpy)) option frag(
        showNonEmptyPerf(u.perfs.ultraBullet, PerfType.UltraBullet),
        showPerf(u.perfs.bullet, PerfType.Bullet),
        showPerf(u.perfs.blitz, PerfType.Blitz),
        showPerf(u.perfs.rapid, PerfType.Rapid),
        showPerf(u.perfs.classical, PerfType.Classical),
        showPerf(u.perfs.correspondence, PerfType.Correspondence),
        br,
        // br, todo variant
        u.noBot option showPerf(u.perfs.puzzle, PerfType.Puzzle),
        u.noBot option showStorm(u.perfs.storm, u)
      )
    )
  }

  private def showStorm(storm: lishogi.rating.Perf.Storm, user: User)(implicit lang: Lang) =
    a(
      dataIcon := '.',
      cls := List(
        "empty" -> !storm.nonEmpty
      ),
      href := routes.Storm.dashboardOf(user.username),
      span(
        h3("Tsume Storm"),
        st.rating(
          strong(storm.score),
          storm.nonEmpty option frag(
            " ",
            span(trans.storm.xRuns.plural(storm.runs, storm.runs.localize))
          )
        )
      ),
      iconTag("G")
    )
}
