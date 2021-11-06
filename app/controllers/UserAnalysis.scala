package controllers

import shogi.format.Forsyth.SituationPlus
import shogi.format.{ FEN, Forsyth }
import shogi.Situation
import shogi.variant.{ FromPosition, Standard, Variant }
import play.api.libs.json.Json
import play.api.mvc._
import scala.concurrent.duration._

import lishogi.api.Context
import lishogi.app._
import lishogi.game.Pov
import lishogi.round.Forecast.{ forecastJsonWriter, forecastStepJsonFormat }
import lishogi.round.JsonView.WithFlags
import lishogi.tree.Node.partitionTreeJsonWriter
import lishogi.study.JsonView.{ kifTagWrites, kifTagsWrites }
import views._

final class UserAnalysis(
    env: Env,
    gameC: => Game
) extends LishogiController(env)
    with TheftPrevention {

  def index = load("", Standard)

  def parseArg(arg: String) =
    arg.split("/", 2) match {
      case Array(_key) => load("", Standard)
      case Array(key, fen) =>
        Variant.byKey get key match {
          //case Some(variant)                 => load(fen, variant)
          case _ if fen == Standard.initialFen => load(arg, Standard)
          case _                               => load(arg, FromPosition)
        }
      case _ => load("", Standard)
    }

  def load(urlFen: String, variant: Variant) =
    Open { implicit ctx =>
      val decodedFen: Option[FEN] = lishogi.common.String
        .decodeUriPath(urlFen)
        .map(_.replace('_', ' ').trim)
        .filter(_.nonEmpty)
        .orElse(get("fen")) map FEN.apply
      val pov         = makePov(decodedFen, variant)
      val orientation = get("color").flatMap(shogi.Color.apply) | pov.color
      env.api.roundApi
        .userAnalysisJson(pov, ctx.pref, decodedFen, orientation, owner = false, me = ctx.me) map { data =>
        EnableSharedArrayBuffer(Ok(html.board.userAnalysis(data, pov)))
      }
    }

  private[controllers] def makePov(fen: Option[FEN], variant: Variant, imported: Boolean = false): Pov = {
    val sitFrom = fen.filter(_.value.nonEmpty).flatMap { f =>
      Forsyth.<<<@(variant, f.value)
    } | SituationPlus(Situation(variant), 1)
    makePov(sitFrom, imported)
  }

  private[controllers] def makePov(from: SituationPlus, imported: Boolean): Pov =
    Pov(
      lishogi.game.Game
        .make(
          shogi = shogi.Game(
            situation = from.situation,
            turns = from.turns,
            startedAtTurn = from.turns
          ),
          sentePlayer = lishogi.game.Player.make(shogi.Sente, none),
          gotePlayer = lishogi.game.Player.make(shogi.Gote, none),
          mode = shogi.Mode.Casual,
          source = if (imported) lishogi.game.Source.Import else lishogi.game.Source.Api,
          notationImport = None
        )
        .withId("synthetic"),
      from.situation.color
    )

  def game(id: String, color: String) =
    Open { implicit ctx =>
      OptionFuResult(env.game.gameRepo game id) { g =>
        env.round.proxyRepo upgradeIfPresent g flatMap { game =>
          val pov = Pov(game, shogi.Color(color == "sente"))
          negotiate(
            html =
              if (game.replayable) Redirect(routes.Round.watcher(game.id, color)).fuccess
              else {
                val owner = isMyPov(pov)
                for {
                  initialFen <- env.game.gameRepo initialFen game.id
                  data <-
                    env.api.roundApi
                      .userAnalysisJson(pov, ctx.pref, initialFen, pov.color, owner = owner, me = ctx.me)
                } yield NoCache(
                  Ok(
                    html.board
                      .userAnalysis(
                        data,
                        pov,
                        withForecast = owner && !pov.game.synthetic && pov.game.playable
                      )
                  )
                )
              },
            api = apiVersion => mobileAnalysis(pov, apiVersion)
          )
        }
      }
    }

  private def mobileAnalysis(pov: Pov, apiVersion: lishogi.common.ApiVersion)(implicit
      ctx: Context
  ): Fu[Result] =
    env.game.gameRepo initialFen pov.gameId flatMap { initialFen =>
      gameC.preloadUsers(pov.game) zip
        (env.analyse.analyser get pov.game) zip
        env.game.crosstableApi(pov.game) flatMap { case _ ~ analysis ~ crosstable =>
          import lishogi.game.JsonView.crosstableWrites
          env.api.roundApi.review(
            pov,
            apiVersion,
            tv = none,
            analysis,
            initialFenO = initialFen.some,
            withFlags = WithFlags(division = true, opening = true, clocks = true, movetimes = true)
          ) map { data =>
            Ok(data.add("crosstable", crosstable))
          }
        }
    }

  // XHR only
  def notation =
    OpenBody { implicit ctx =>
      implicit val req = ctx.body
      lishogi.study.StudyForm.importFree.form
        .bindFromRequest()
        .fold(
          jsonFormError,
          data =>
            lishogi.study.NotationImport
              .userAnalysis(data.notation)
              .fold(
                err => BadRequest(err.toList mkString "\n").fuccess,
                { case (game, initialFen, root, tags) =>
                  val pov = Pov(game, shogi.Sente)
                  val baseData = env.round.jsonView
                    .userAnalysisJson(
                      pov,
                      ctx.pref,
                      initialFen,
                      pov.color,
                      owner = false,
                      me = ctx.me
                    )
                  Ok(
                    baseData ++ Json.obj(
                      "treeParts" -> partitionTreeJsonWriter.writes {
                        lishogi.study.TreeBuilder(root, pov.game.variant)
                      },
                      "tags" -> tags
                    )
                  ).fuccess
                }
              )
        )
        .map(_ as JSON)
    }

  def forecasts(fullId: String) =
    AuthBody(parse.json) { implicit ctx => _ =>
      import lishogi.round.Forecast
      OptionFuResult(env.round.proxyRepo pov fullId) { pov =>
        if (isTheft(pov)) fuccess(theftResponse)
        else
          ctx.body.body
            .validate[Forecast.Steps]
            .fold(
              err => BadRequest(err.toString).fuccess,
              forecasts =>
                env.round.forecastApi.save(pov, forecasts) >>
                  env.round.forecastApi.loadForDisplay(pov) map {
                    case None     => Ok(Json.obj("none" -> true))
                    case Some(fc) => Ok(Json toJson fc) as JSON
                  } recover { case Forecast.OutOfSync =>
                    Ok(Json.obj("reload" -> true))
                  }
            )
      }
    }

  def forecastsOnMyTurn(fullId: String, uci: String) =
    AuthBody(parse.json) { implicit ctx => _ =>
      import lishogi.round.Forecast
      OptionFuResult(env.round.proxyRepo pov fullId) { pov =>
        if (isTheft(pov)) fuccess(theftResponse)
        else
          ctx.body.body
            .validate[Forecast.Steps]
            .fold(
              err => BadRequest(err.toString).fuccess,
              forecasts => {
                val wait = 50 + (Forecast maxPlies forecasts min 10) * 50
                env.round.forecastApi.playAndSave(pov, uci, forecasts) >>
                  lishogi.common.Future.sleep(wait.millis) inject
                  Ok(Json.obj("reload" -> true))
              }
            )
      }
    }

  def help =
    Open { implicit ctx =>
      Ok(html.analyse.help(getBool("study"))).fuccess
    }
}
