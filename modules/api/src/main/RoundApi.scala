package lila.api

import play.api.libs.json._

import lila.analyse.Analysis
import lila.analyse.{ JsonView => analysisJson }
import lila.game.Game
import lila.game.Pov
import lila.pref.Pref
import lila.round.Forecast
import lila.round.JsonView
import lila.round.JsonView.WithFlags
import lila.security.Granter
import lila.simul.Simul
import lila.tournament.{ GameView => TourView }
import lila.tree.Node.partitionTreeJsonWriter
import lila.user.User

final private[api] class RoundApi(
    jsonView: JsonView,
    forecastApi: lila.round.ForecastApi,
    bookmarkApi: lila.bookmark.BookmarkApi,
    tourApi: lila.tournament.TournamentApi,
    simulApi: lila.simul.SimulApi,
    getTeamName: lila.team.GetTeamName,
    getLightUser: lila.common.LightUser.GetterSync,
)(implicit ec: scala.concurrent.ExecutionContext) {

  def player(pov: Pov, tour: Option[TourView])(implicit
      ctx: Context,
  ): Fu[JsObject] = {
    jsonView.playerJson(
      pov,
      ctx.pref,
      ctx.me,
      withFlags = WithFlags(blurs = ctx.me ?? Granter(_.ViewBlurs)),
      nvui = ctx.blind,
    ) zip
      (pov.game.simulId ?? simulApi.find) zip
      forecastApi.loadForDisplay(pov) zip
      bookmarkApi.exists(pov.game, ctx.me) map { case (((json, simul), forecast), bookmarked) =>
        (
          withTournament(pov, tour) _ compose
            withSimul(simul) compose
            withSteps(pov) compose
            withBookmark(bookmarked) compose
            withForecastCount(forecast.map(_.steps.size))
        )(json)
      }
  }
    .mon(_.round.api.player)

  def watcher(
      pov: Pov,
      tour: Option[TourView],
      tv: Option[lila.round.OnTv],
  )(implicit ctx: Context): Fu[JsObject] = {
    jsonView.watcherJson(
      pov,
      ctx.pref,
      ctx.me,
      tv,
      withFlags = WithFlags(blurs = ctx.me ?? Granter(_.ViewBlurs)),
    ) zip
      (pov.game.simulId ?? simulApi.find) zip
      bookmarkApi.exists(pov.game, ctx.me) map { case ((json, simul), bookmarked) =>
        (
          withTournament(pov, tour) _ compose
            withSimul(simul) compose
            withBookmark(bookmarked) compose
            withSteps(pov)
        )(json)
      }
  }
    .mon(_.round.api.watcher)

  def review(
      pov: Pov,
      tv: Option[lila.round.OnTv] = None,
      analysis: Option[Analysis] = None,
      withFlags: WithFlags,
  )(implicit ctx: Context): Fu[JsObject] = {
    jsonView.watcherJson(
      pov,
      ctx.pref,
      ctx.me,
      tv,
      withFlags = withFlags.copy(blurs = ctx.me ?? Granter(_.ViewBlurs)),
    ) zip
      tourApi.gameView.analysis(pov.game) zip
      (pov.game.simulId ?? simulApi.find) zip
      bookmarkApi.exists(pov.game, ctx.me) map { case (((json, tour), simul), bookmarked) =>
        (
          withTournament(pov, tour) _ compose
            withSimul(simul) compose
            withBookmark(bookmarked) compose
            withTree(pov, analysis, withFlags) compose
            withAnalysis(pov.game, analysis)
        )(json)
      }
  }
    .mon(_.round.api.watcher)

  def embed(
      pov: Pov,
      analysis: Option[Analysis] = None,
      withFlags: WithFlags,
  ): Fu[JsObject] = {
    jsonView.watcherJson(
      pov,
      Pref.default,
      none,
      none,
      withFlags = withFlags,
    ) map { json =>
      (
        withTree(pov, analysis, withFlags) _ compose
          withAnalysis(pov.game, analysis) _
      )(json)
    }
  }
    .mon(_.round.api.embed)

  def userAnalysisJson(
      pov: Pov,
      pref: Pref,
      orientation: shogi.Color,
      owner: Boolean,
      me: Option[User],
  ) =
    owner.??(forecastApi loadForDisplay pov).map { fco =>
      withForecast(pov, owner, fco) {
        withTree(pov, analysis = none, WithFlags()) {
          jsonView.userAnalysisJson(pov, pref, orientation, owner = owner, me = me)
        }
      }
    }

  def freeStudyJson(
      pov: Pov,
      pref: Pref,
      orientation: shogi.Color,
      me: Option[User],
  ) =
    withTree(pov, analysis = none, WithFlags())(
      jsonView.userAnalysisJson(pov, pref, orientation, owner = false, me = me),
    )

  private def withTree(pov: Pov, analysis: Option[Analysis], withFlags: WithFlags)(
      obj: JsObject,
  ) =
    obj + ("treeParts" -> partitionTreeJsonWriter.writes(
      lila.round.TreeBuilder(pov.game, analysis, withFlags),
    ))

  private def withSteps(pov: Pov)(obj: JsObject) =
    obj + ("steps" -> lila.round.StepBuilder(
      id = pov.gameId,
      usis = pov.game.usis,
      variant = pov.game.variant,
      initialSfen = pov.game.initialSfen,
    ))

  private def withBookmark(v: Boolean)(json: JsObject) =
    json.add("bookmarked" -> v)

  private def withForecastCount(count: Option[Int])(json: JsObject) =
    count.filter(0 !=).fold(json) { c =>
      json + ("forecastCount" -> JsNumber(c))
    }

  private def withForecast(pov: Pov, owner: Boolean, fco: Option[Forecast])(json: JsObject) =
    if (pov.game.forecastable && owner)
      json + (
        "forecast" -> {
          if (pov.forecastable) fco.fold[JsValue](Json.obj("none" -> true)) { fc =>
            import Forecast.forecastJsonWriter
            Json toJson fc
          }
          else Json.obj("onMyTurn" -> true)
        }
      )
    else json

  private def withAnalysis(g: Game, o: Option[Analysis])(json: JsObject) =
    json.add(
      "analysis",
      o.map { a =>
        analysisJson.bothPlayers(g, a)
      },
    )

  def withTournament(pov: Pov, viewO: Option[TourView])(json: JsObject) =
    json.add("tournament" -> viewO.map { v =>
      Json
        .obj(
          "id"      -> v.tour.id,
          "name"    -> v.tour.name,
          "format"  -> v.tour.format.key,
          "running" -> v.tour.isStarted,
        )
        .add("secondsToFinish" -> v.tour.isStarted.option(v.tour.secondsToFinish))
        .add("berserkable" -> v.tour.isStarted.option(v.tour.berserkable))
        // mobile app API BC / should use game.expiration instead
        .add("nbSecondsForFirstMove" -> v.tour.isStarted.option {
          pov.game.timeForFirstMove.toSeconds
        })
        .add("ranks" -> v.ranks.map { r =>
          Json.obj(
            "sente" -> r.senteRank,
            "gote"  -> r.goteRank,
          )
        })
        .add(
          "top",
          v.top.map {
            lila.tournament.JsonView.top(_, getLightUser)
          },
        )
        .add(
          "team",
          v.teamVs.map(_.teams(pov.color)) map { id =>
            Json.obj("name" -> getTeamName(id))
          },
        )
    })

  private def withSimul(simulOption: Option[Simul])(json: JsObject) =
    json.add(
      "simul",
      simulOption.map { simul =>
        Json.obj(
          "id"        -> simul.id,
          "hostId"    -> simul.hostId,
          "name"      -> simul.name,
          "nbPlaying" -> simul.playingPairings.size,
        )
      },
    )
}
