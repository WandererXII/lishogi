package views.html.user.show

import controllers.routes
import play.api.i18n.Lang

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.rating.PerfType
import lila.user.User

object side {

  def apply(
      u: User,
      rankMap: lila.rating.UserRankMap,
      active: Option[lila.rating.PerfType],
  )(implicit ctx: Context) = {

    def showNonEmptyPerf(perf: lila.rating.Perf, perfType: PerfType) =
      perf.nonEmpty option showPerf(perf, perfType)

    def showPerf(perf: lila.rating.Perf, perfType: PerfType) = {
      val isPuzzle = perfType == lila.rating.PerfType.Puzzle
      a(
        dataIcon := perfType.icon,
        cls := List(
          "perf-item"   -> true,
          "perf-puzzle" -> isPuzzle,
          "empty"       -> perf.isEmpty,
          "active"      -> active.has(perfType),
        ),
        href := {
          if (isPuzzle) ctx.is(u) option routes.Puzzle.dashboard(30, "home").url
          else routes.User.perfStat(u.username, perfType.key).url.some
        },
        div(
          h3(perfType.trans),
          !isPuzzle option strong(cls := "perf-rank")(
            rankTag(perf, withUnknown = true),
          ),
          div(cls := "perf-meta")(
            st.rating(
              span(cls := "perf-rating")(
                span(cls := "perf-rating-int")(
                  perf.glicko.intRating,
                  perf.provisional option "?",
                ),
                " ",
                showRatingProgress(perf.progress),
              ),
            ),
            span(cls := "perf-cnt")(
              if (perfType.key == "puzzle") trans.nbPuzzles.plural(perf.nb, perf.nb.localize)
              else trans.nbGames.plural(perf.nb, perf.nb.localize),
            ),
          ),
          rankMap get perfType map { rank =>
            span(cls := "rank", title := trans.rankIsUpdatedEveryNbMinutes.pluralSameTxt(15))(
              trans.rankX(rank.localize),
            )
          },
        ),
        !isPuzzle option iconTag(Icons.play),
      )
    }

    div(cls := "side sub-ratings")(
      (!u.lame || ctx.is(u) || isGranted(_.UserSpy)) option frag(
        showPerf(u.perfs.realTime, PerfType.RealTime),
        showPerf(u.perfs.correspondence, PerfType.Correspondence),
        br,
        showNonEmptyPerf(u.perfs.minishogi, PerfType.Minishogi),
        showNonEmptyPerf(u.perfs.chushogi, PerfType.Chushogi),
        showNonEmptyPerf(u.perfs.annanshogi, PerfType.Annanshogi),
        showNonEmptyPerf(u.perfs.kyotoshogi, PerfType.Kyotoshogi),
        showNonEmptyPerf(u.perfs.checkshogi, PerfType.Checkshogi),
        showNonEmptyPerf(u.perfs.dobutsu, PerfType.Dobutsu),
        br,
        u.noBot option showPerf(u.perfs.puzzle, PerfType.Puzzle),
        (u.noBot && u.perfs.storm.nonEmpty) option showStorm(u.perfs.storm, u),
        u.noBot option br,
        u.perfs.aiLevels.standard.ifTrue(u.noBot).map(l => aiLevel(l, shogi.variant.Standard)),
        u.perfs.aiLevels.minishogi.ifTrue(u.noBot).map(l => aiLevel(l, shogi.variant.Minishogi)),
        u.perfs.aiLevels.kyotoshogi.ifTrue(u.noBot).map(l => aiLevel(l, shogi.variant.Kyotoshogi)),
      ),
    )
  }

  private def showStorm(storm: lila.rating.Perf.Storm, user: User)(implicit lang: Lang) =
    a(
      dataIcon := Icons.storm,
      cls := List(
        "perf-item"  -> true,
        "perf-storm" -> true,
        "empty"      -> !storm.nonEmpty,
      ),
      href := routes.Storm.dashboardOf(user.username),
      div(
        h3("Storm"),
        div(cls := "perf-meta")(
          st.rating(
            span(cls := "perf-rating-int")(
              trans.storm.highscoreX(strong(storm.score)),
            ),
          ),
          span(cls := "perf-cnt")(
            span(trans.storm.xRuns.plural(storm.runs, storm.runs.localize)),
          ),
        ),
      ),
    )

  private def aiLevel(level: Int, variant: shogi.variant.Variant)(implicit lang: Lang) =
    div(
      dataIcon := Icons.cogs,
      cls      := s"perf-item ai-level ai-level-$level",
      span(
        h3(variantName(variant)),
        div(cls := "ai-level")(
          trans.defeatedAiNameAiLevel.txt("AI", level),
        ),
      ),
    )
}
