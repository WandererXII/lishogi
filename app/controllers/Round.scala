package controllers

import play.api.libs.json._
import play.api.mvc._
import views._

import lila.api.Context
import lila.app._
import lila.chat.Chat
import lila.common.HTTPRequest
import lila.game.NotationDump
import lila.game.Pov
import lila.game.{ Game => GameModel }
import lila.socket.Socket.SocketVersion
import lila.user.{ User => UserModel }

final class Round(
    env: Env,
    gameC: => Game,
    challengeC: => Challenge,
    analyseC: => Analyse,
) extends LilaController(env)
    with TheftPrevention {

  def gameOrChallengeDefault(gameId: String) = gameOrChallenge(gameId, shogi.Color.Sente.name)

  // challenge -> game -> analysis
  def gameOrChallenge(gameId: String, color: String) =
    Open { implicit ctx =>
      env.round.proxyRepo.gameWithVersion(gameId) flatMap {
        case Some((game, socketVersion)) if game.replayable && !getBool("game") =>
          analyseC.replay(Pov(game, parseColorString(game, color)), socketVersion, userTv = none)
        case Some((game, socketVersion)) =>
          myGameColor(game) match {
            case Some(myColor) => renderPlayer(Pov(game, myColor), socketVersion)
            case None => renderWatcher(Pov(game, parseColorString(game, color)), socketVersion)
          }
        case None => challengeC showId gameId
      }
    }

  private def analyser = env.analyse.analyser

  private def renderPlayer(pov: Pov, socketVersion: SocketVersion)(implicit
      ctx: Context,
  ): Fu[Result] =
    negotiate(
      html = {
        if (!pov.game.started) notFound
        else
          pov.game.playableByAi ?? env.shoginet.player(pov.game)
        env.tournament.api.gameView.player(pov) flatMap { tour =>
          gameC.preloadUsers(pov.game) zip
            (pov.game.simulId ?? env.simul.repo.find) zip
            getGameChat(pov.game) zip
            (ctx.noBlind ?? env.game.crosstableApi
              .withMatchup(pov.game)) zip
            (pov.game.isSwitchable ?? otherPovs(pov.game)) zip
            env.bookmark.api.exists(pov.game, ctx.me) zip
            env.api.roundApi.player(pov, tour) map {
              case ((((((_, simul), chatOption), crosstable), playing), bookmarked), data) =>
                simul foreach env.simul.api.onPlayerConnection(pov.game, ctx.me)
                Ok(
                  html.round.player(
                    pov = pov,
                    data = data,
                    socketVersion = socketVersion,
                    tour = tour,
                    simul = simul,
                    cross = crosstable,
                    playing = playing,
                    chatOption = chatOption,
                    bookmarked = bookmarked,
                  ),
                ).noCache
            }
        }
      },
      json = {
        env.tournament.api.gameView.mobile(pov.game) flatMap { tour =>
          pov.game.playableByAi ?? env.shoginet.player(pov.game)
          gameC.preloadUsers(pov.game) zip
            env.api.roundApi.player(pov, tour) zip
            getGameChat(pov.game) map { case ((_, data), chat) =>
              Ok(
                data.add("chat", chat.map(c => lila.chat.JsonView(c.chat))),
              ).noCache
            }
        }
      },
    )

  private[controllers] def renderWatcher(
      pov: Pov,
      socketVersion: SocketVersion,
      userTv: Option[UserModel] = None,
  )(implicit ctx: Context): Fu[Result] =
    negotiate(
      html = {
        if (HTTPRequest.isHuman(ctx.req))
          env.tournament.api.gameView.watcher(pov.game) zip
            (pov.game.simulId ?? env.simul.repo.find) zip
            getGameChat(pov.game) zip
            (ctx.noBlind ?? env.game.crosstableApi.withMatchup(pov.game)) zip
            env.bookmark.api.exists(pov.game, ctx.me) flatMap {
              case ((((tour, simul), chat), crosstable), bookmarked) =>
                env.api.roundApi.watcher(
                  pov,
                  tour,
                  tv = userTv.map { u =>
                    lila.round.OnUserTv(u.id)
                  },
                ) map { data =>
                  Ok(
                    html.round.watcher(
                      pov,
                      data,
                      socketVersion,
                      tour.map(_.tourAndTeamVs),
                      simul,
                      crosstable,
                      userTv = userTv,
                      chatOption = chat,
                      bookmarked = bookmarked,
                    ),
                  )
                }
            }
        else
          for { // web crawlers don't need the full thing
            kif <- env.api
              .notationDump(pov.game, none, NotationDump.WithFlags(clocks = false))
          } yield Ok(html.round.watcher.crawler(pov, kif))
      },
      json = for {
        data     <- env.api.roundApi.watcher(pov, none, tv = none)
        analysis <- analyser get pov.game
        chat     <- getGameChat(pov.game)
      } yield Ok {
        data
          .add("chat" -> chat.map(c => lila.chat.JsonView(c.chat)))
          .add("analysis" -> analysis.map(a => lila.analyse.JsonView.mobile(pov.game, a)))
      },
    ) dmap (_.noCache)

  // BC
  def gameFullId(fullId: String) =
    Open { implicit ctx =>
      val gameId   = lila.game.Game.takeGameId(fullId)
      val playerId = lila.game.Game.takePlayerId(fullId)
      OptionFuResult(env.round.proxyRepo.game(gameId)) { game =>
        Pov(game, playerId).fold(fuccess(Redirect(routes.Round.gameOrChallengeDefault(gameId)))) {
          pov =>
            fuccess(Redirect(routes.Round.gameOrChallenge(pov.gameId, pov.color.name)))
        }
      }
    }

  private def parseColorString(game: lila.game.Game, str: String): shogi.Color =
    shogi.Color
      .fromName(str)
      .orElse(game.playerByUserId(str.toLowerCase).map(_.color))
      .getOrElse(game.firstColor)

  private def otherPovs(game: GameModel)(implicit ctx: Context): Fu[List[Pov]] =
    ctx.me ?? { user =>
      env.round.proxyRepo.urgentGames(user) map {
        _ filter { pov =>
          pov.gameId != game.id && pov.game.isSwitchable && pov.game.isSimul == game.isSimul
        }
      }
    }

  private def getNext(currentGame: GameModel)(povs: List[Pov]): Option[Pov] =
    povs find { pov =>
      pov.isMyTurn && (pov.game.hasClock || !currentGame.hasClock)
    }

  def whatsNext(gameId: String) =
    Open { implicit ctx =>
      OptionFuResult(env.round.proxyRepo.game(gameId)) { currentGame =>
        if (myGameColor(currentGame).exists(color => Pov(currentGame, color).isMyTurn)) fuccess {
          Ok(Json.obj("nope" -> true))
        }
        else
          otherPovs(currentGame) map getNext(currentGame) map { next =>
            Ok(Json.obj("next" -> next.map(_.gameId)))
          }
      }
    }

  def next(gameId: String) =
    Auth { implicit ctx => me =>
      OptionFuResult(env.round.proxyRepo game gameId) { currentGame =>
        otherPovs(currentGame) map getNext(currentGame) map {
          _ orElse Pov(currentGame, me)
        } flatMap { next =>
          next ?? { p => env.round.proxyRepo.povWithVersion(p.fullId) }
        } flatMap {
          case Some((pov, socketVersion)) =>
            renderPlayer(pov, socketVersion)
          case _ =>
            fuccess(Redirect(currentGame.simulId match {
              case Some(simulId) => routes.Simul.show(simulId)
              case None          => routes.Round.gameOrChallengeDefault(gameId)
            }))
        }
      }
    }

  private[controllers] def getGameChat(game: GameModel)(implicit
      ctx: Context,
  ): Fu[Option[Chat.Game]] =
    (ctx.noKid && ctx.me.fold(HTTPRequest isHuman ctx.req)(env.chat.panic.allowed)) ?? {
      env.chat.api.findMineIf(Chat.Id(game.id), ctx.me, !game.justCreated) flatMap { mine =>
        env.user.lightUserApi.preloadMany(mine.chat.userIds) inject Chat
          .Game(
            mine.chat,
            timeout = mine.timeout,
            restricted = game.fromLobby && ctx.isAnon,
          )
          .some
      }
    }

  def sides(gameId: String, color: String) =
    Open { implicit ctx =>
      OptionFuResult(env.round.proxyRepo.game(gameId)) { case game =>
        env.tournament.api.gameView.withTeamVs(game) zip
          (game.simulId ?? env.simul.repo.find) zip
          env.game.crosstableApi.withMatchup(game) zip
          env.bookmark.api.exists(game, ctx.me) map {
            case (((tour, simul), crosstable), bookmarked) =>
              Ok(
                html.game.bits.sides(
                  Pov(game, parseColorString(game, color)),
                  tour,
                  crosstable,
                  simul,
                  bookmarked = bookmarked,
                ),
              )
          }
      }
    }

  def writeNote(gameId: String) =
    AuthBody { implicit ctx => me =>
      import play.api.data.Forms._
      import play.api.data._
      implicit val req = ctx.body
      Form(single("text" -> text))
        .bindFromRequest()
        .fold(
          _ => fuccess(BadRequest),
          text => env.round.noteApi.set(gameId, me.id, text.trim take 10000),
        )
    }

  def readNote(gameId: String) =
    Auth { _ => me =>
      env.round.noteApi.get(gameId, me.id) dmap { Ok(_) }
    }

  def continue(id: String, mode: String) =
    Open { implicit ctx =>
      OptionResult(env.game.gameRepo game id) { game =>
        Redirect(
          "%s?sfen=%s#%s".format(
            routes.Lobby.home,
            get("sfen") | (game.shogi.toSfen).value,
            mode,
          ),
        )
      }
    }

  def resignPost(gameId: String) =
    Open { implicit ctx =>
      OptionFuRedirect(env.round.proxyRepo.game(gameId)) { game =>
        myGameColor(game, !game.replayable) match {
          case Some(color) =>
            import scala.concurrent.duration._
            val pov = Pov(game, color)
            env.round resign pov
            akka.pattern.after(500.millis, env.system.scheduler)(fuccess(routes.Lobby.home))
          case _ =>
            lila
              .log("round")
              .warn(s"theft resign $gameId ${HTTPRequest.lastRemoteAddress(ctx.req)}")
            fuccess(routes.Lobby.home)
        }
      }
    }

  def miniDefault(gameId: String) =
    mini(gameId, shogi.Color.Sente.name)

  def mini(gameId: String, color: String) =
    Open { implicit ctx =>
      OptionOk(
        env.round.proxyRepo.gameIfPresent(gameId) orElse env.game.gameRepo.game(gameId) map2 { g =>
          Pov(g, parseColorString(g, color))
        },
      )(html.game.bits.mini)
    }

  def miniFullId(fullId: String) =
    Open { implicit ctx =>
      OptionOk(env.round.proxyRepo.povIfPresent(fullId) orElse env.game.gameRepo.pov(fullId))(
        html.game.bits.mini,
      )
    }
}
