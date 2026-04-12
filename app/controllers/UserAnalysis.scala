package controllers

import scala.concurrent.duration._

import play.api.libs.json.Json
import views._

import shogi.format.Reader
import shogi.format.forsyth.Sfen
import shogi.format.usi.Usi
import shogi.variant.Standard
import shogi.variant.Variant

import lila.app._
import lila.game.Pov
import lila.round.Forecast.forecastJsonWriter
import lila.round.Forecast.forecastStepJsonFormat
import lila.study.JsonView.tagsWrites

final class UserAnalysis(
    env: Env,
) extends LilaController(env)
    with TheftPrevention {

  def index = load("", Standard)

  def parseArg(arg: String) =
    arg.split("/", 2) match {
      case Array(key) => load("", Variant orDefault key)
      case Array(key, sfen) =>
        Variant.byKey get key match {
          case Some(variant) => load(sfen, variant)
          case _             => load(arg, Standard)
        }
      case _ => load("", Standard)
    }

  def load(urlSfen: String, variant: Variant) =
    Open { implicit ctx =>
      val decodedSfen: Option[Sfen] = lila.common.String
        .decodeUriPath(urlSfen)
        .filter(_.trim.nonEmpty)
        .orElse(get("sfen")) map Sfen.clean
      val usis = get("moves").flatMap(ms => Usi.readList(ms.split('_').toList.take(300)))
      val pov  = usis.fold(makePov(decodedSfen, variant))(makePovWithUsis(decodedSfen, variant, _))
      val orientation = get("color").flatMap(shogi.Color.fromName) | pov.color
      env.api.roundApi
        .userAnalysisJson(
          pov,
          ctx.pref,
          orientation,
          owner = false,
          me = ctx.me,
        ) map { data =>
        Ok(html.board.userAnalysis(data, pov)).enableSharedArrayBuffer
      }
    }

  private[controllers] def makePov(
      sfen: Option[Sfen],
      variant: Variant,
      imported: Boolean = false,
  ): Pov = {
    val g = shogi.Game(sfen, variant)
    makePovWithShogi(g, g.toSfen.some, imported)
  }

  private def makePovWithUsis(
      sfen: Option[Sfen],
      variant: Variant,
      usis: List[Usi],
      imported: Boolean = false,
  ): Pov =
    makePovWithShogi(
      shogi.Replay(
        usis = usis,
        initialSfen = sfen,
        variant = variant,
      ) match {
        case Reader.Result.Complete(replay)      => replay.state
        case Reader.Result.Incomplete(replay, _) => replay.state
      },
      sfen.flatMap(sf => sf.toSituationPlus(variant).map(_.toSfen)),
      imported,
    )

  private def makePovWithShogi(
      shogiGame: shogi.Game,
      initialSfen: Option[Sfen],
      imported: Boolean,
  ): Pov =
    Pov(
      lila.game.Game
        .make(
          shogi = shogiGame,
          initialSfen = initialSfen,
          sentePlayer = lila.game.Player.make(shogi.Sente),
          gotePlayer = lila.game.Player.make(shogi.Gote),
          mode = shogi.Mode.Casual,
          proMode = false,
          source = if (imported) lila.game.Source.Import else lila.game.Source.Api,
          notationImport = None,
        )
        .withId("synthetic"),
      initialSfen.flatMap(_.color) | shogi.Color.Sente,
    )

  def forecastsBoard(gameId: String) =
    Open { implicit ctx =>
      OptionFuResult(env.round.proxyRepo game gameId) { game =>
        myGameColor(game, !game.synthetic && game.playable) match {
          case Some(myColor) =>
            val pov = Pov(game, myColor)
            for {
              data <-
                env.api.roundApi
                  .userAnalysisJson(pov, ctx.pref, pov.color, owner = true, me = ctx.me)
            } yield Ok(
              html.board
                .userAnalysis(
                  data,
                  pov,
                  withForecast = true,
                ),
            ).noCache
          case _ => Redirect(routes.Round.gameOrChallengeDefault(game.id)).fuccess
        }
      }
    }

  // XHR only
  def notation =
    OpenBody { implicit ctx =>
      implicit val req = ctx.body
      lila.study.StudyForm.importFree.form
        .bindFromRequest()
        .fold(
          jsonFormError,
          data =>
            lila.study.NotationImport
              .userAnalysis(data.notation)
              .fold(
                err => BadRequest(err).fuccess,
                { case (game, root, tags) =>
                  val pov = Pov(game, shogi.Sente)
                  val baseData = env.round.jsonView
                    .userAnalysisJson(
                      pov,
                      ctx.pref,
                      pov.color,
                      owner = false,
                      me = ctx.me,
                    )
                  Ok(
                    baseData ++ Json.obj(
                      "treeParts" -> lila.study.JsonView.partitionTreeJsonWriter.writes(
                        root,
                      ),
                      "tags" -> tags,
                    ),
                  ).fuccess
                },
              ),
        )
        .map(_ as JSON)
    }

  def forecasts(gameId: String) =
    AuthBody(parse.json) { implicit ctx => _ =>
      import lila.round.Forecast
      OptionFuResult(env.round.proxyRepo game gameId) { game =>
        myGameColor(game, game.playableEvenPaused) match {
          case Some(myColor) =>
            val pov = Pov(game, myColor)
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
                    },
              )
          case None => fuccess(theftResponse)
        }
      }
    }

  def forecastsOnMyTurn(gameId: String, usi: String) =
    AuthBody(parse.json) { implicit ctx => _ =>
      import lila.round.Forecast
      OptionFuResult(env.round.proxyRepo game gameId) { game =>
        myGameColor(game, game.playableEvenPaused) match {
          case Some(myColor) =>
            val pov = Pov(game, myColor)
            ctx.body.body
              .validate[Forecast.Steps]
              .fold(
                err => BadRequest(err.toString).fuccess,
                forecasts => {
                  val wait = 50 + (Forecast maxPlies forecasts min 10) * 50
                  env.round.forecastApi.playAndSave(pov, usi, forecasts) >>
                    lila.common.Future.sleep(wait.millis) inject
                    Ok(Json.obj("reload" -> true))
                },
              )
          case None => fuccess(theftResponse)
        }
      }
    }

  def help =
    Open { implicit ctx =>
      Ok(html.analyse.help(getBool("study"))).fuccess
    }
}
