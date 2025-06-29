package controllers

import java.nio.charset.StandardCharsets.UTF_8

import play.api.mvc._

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

import lila.api.GameApiV2
import lila.app._
import lila.common.HTTPRequest
import lila.common.String.getBytesShiftJis
import lila.common.config.MaxPerSecond
import lila.game.{ Game => GameModel }

final class Game(
    env: Env,
    apiC: => Api,
) extends LilaController(env) {

  def delete(gameId: String) =
    Auth { implicit ctx => me =>
      OptionFuResult(env.game.gameRepo game gameId) { game =>
        if (game.notationImport.flatMap(_.user) ?? (me.id.==)) {
          env.hub.bookmark ! lila.hub.actorApi.bookmark.Remove(game.id)
          (env.game.gameRepo remove game.id) >>
            (env.analyse.analysisRepo remove game.id) >>
            env.game.cached.clearNbImportedByCache(me.id) inject
            Redirect(routes.User.show(me.username))
        } else
          fuccess {
            Redirect(routes.Round.watcher(game.id, game.firstColor.name))
          }
      }
    }

  def exportOne(id: String) = Action.async { exportGame(id, _) }

  private[controllers] def exportGame(gameId: GameModel.ID, req: RequestHeader): Fu[Result] =
    env.round.proxyRepo.gameIfPresent(gameId) orElse env.game.gameRepo.game(gameId) flatMap {
      case None => NotFound.fuccess
      case Some(game) =>
        lila.mon.notation.game.increment()
        val config = GameApiV2.OneConfig(
          format =
            if (HTTPRequest acceptsJson req) GameApiV2.Format.JSON else GameApiV2.Format.NOTATION,
          imported = getBool("imported", req),
          flags = requestNotationFlags(req, extended = true),
          noDelay = get("key", req).exists(env.noDelaySecretSetting.get().value.contains),
          playerFile = get("players", req),
        )
        env.api.gameApiV2.exportOne(game, config) flatMap { content =>
          env.api.gameApiV2.filename(game, config) map { filename =>
            val contentEncoded =
              if (config.flags.shiftJis) getBytesShiftJis(content) else content.getBytes(UTF_8)
            Ok(contentEncoded)
              .withHeaders(
                CONTENT_DISPOSITION -> s"attachment; filename=$filename",
              )
              .withHeaders(
                lila.app.http.ResponseHeaders.headersForApiOrApp(req): _*,
              ) as gameContentType(config)
          }
        }
    }

  def exportByUser(username: String) =
    OpenOrScoped()(
      open = ctx => handleExport(username, ctx.me, ctx.req, oauth = false),
      scoped = req => me => handleExport(username, me.some, req, oauth = true),
    )

  def apiExportByUser(username: String) =
    AnonOrScoped()(
      anon = req => handleExport(username, none, req, oauth = false),
      scoped = req => me => handleExport(username, me.some, req, oauth = true),
    )

  private def handleExport(
      username: String,
      me: Option[lila.user.User],
      req: RequestHeader,
      oauth: Boolean,
  ) =
    env.user.repo named username flatMap {
      _ ?? { user =>
        val format = GameApiV2.Format byRequest req
        WithVs(req) { vs =>
          val config = GameApiV2.ByUserConfig(
            user = user,
            format = format,
            vs = vs,
            since = getLong("since", req) map { new DateTime(_) },
            until = getLong("until", req) map { new DateTime(_) },
            max = getInt("max", req) map (_ atLeast 1),
            rated = getBoolOpt("rated", req),
            perfType = (~get("perfType", req) split "," flatMap { lila.rating.PerfType(_) }).toSet,
            color = get("color", req) flatMap shogi.Color.fromName,
            analysed = getBoolOpt("analysed", req),
            ongoing = getBool("ongoing", req),
            flags = requestNotationFlags(req, extended = false),
            perSecond = MaxPerSecond(me match {
              case Some(m) if m is user.id => 60
              case Some(_) if oauth        => 30 // bonus for oauth logged in only (not for CSRF)
              case _                       => 20
            }),
            playerFile = get("players", req),
          )
          val date = DateTimeFormat forPattern "yyyy-MM-dd" print new DateTime
          apiC
            .GlobalConcurrencyLimitPerIpAndUserOption(req, me)(
              env.api.gameApiV2.exportByUser(config),
            ) { source =>
              Ok.chunked(source)
                .withHeaders(
                  noProxyBufferHeader,
                  CONTENT_DISPOSITION -> s"attachment; filename=lishogi_${user.username}_$date.${format.toString.toLowerCase}",
                )
                .as(gameContentType(config))
            }
            .fuccess
        }
      }
    }

  def exportByIds =
    Action.async(parse.tolerantText) { req =>
      val config = GameApiV2.ByIdsConfig(
        ids = req.body.split(',').view.take(300).toSeq,
        format = GameApiV2.Format byRequest req,
        flags = requestNotationFlags(req, extended = false),
        perSecond = MaxPerSecond(20),
        playerFile = get("players", req),
      )
      apiC
        .GlobalConcurrencyLimitPerIP(HTTPRequest lastRemoteAddress req)(
          env.api.gameApiV2.exportByIds(config),
        ) { source =>
          noProxyBuffer(Ok.chunked(source)).as(gameContentType(config))
        }
        .fuccess
    }

  private def WithVs(req: RequestHeader)(f: Option[lila.user.User] => Fu[Result]): Fu[Result] =
    get("vs", req) match {
      case None => f(none)
      case Some(name) =>
        env.user.repo named name flatMap {
          case None       => notFoundJson(s"No such opponent: $name")
          case Some(user) => f(user.some)
        }
    }

  private[controllers] def requestNotationFlags(req: RequestHeader, extended: Boolean) =
    lila.game.NotationDump.WithFlags(
      csa = getBoolOpt("csa", req) | false,
      moves = getBoolOpt("moves", req) | true,
      tags = getBoolOpt("tags", req) | true,
      clocks = getBoolOpt("clocks", req) | extended,
      evals = getBoolOpt("evals", req) | extended,
      shiftJis = getBoolOpt("shiftJis", req) | false,
      notationInJson = getBoolOpt("notationInJson", req) | false,
    )

  private[controllers] def gameContentType(config: GameApiV2.Config) =
    config.format match {
      case GameApiV2.Format.NOTATION => notationContentType
      case GameApiV2.Format.JSON =>
        config match {
          case _: GameApiV2.OneConfig => JSON
          case _                      => ndJsonContentType
        }
    }

  private[controllers] def preloadUsers(game: GameModel): Funit =
    env.user.lightUserApi preloadMany game.userIds
}
