package lila.round

import scala.concurrent.duration._

import shogi.Clock
import shogi.Color.Gote
import shogi.Color.Sente
import shogi.{ Color => ShogiColor }
import shogi.{ Game => ShogiGame }

import lila.common.Bus
import lila.game.AnonCookie
import lila.game.Event
import lila.game.Game
import lila.game.GameRepo
import lila.game.Pov
import lila.game.Rematches
import lila.game.Source
import lila.i18n.{ I18nKeys => trans }
import lila.user.User
import lila.user.UserRepo

final private class Rematcher(
    gameRepo: GameRepo,
    userRepo: UserRepo,
    idGenerator: lila.game.IdGenerator,
    messenger: Messenger,
    onStart: OnStart,
    rematches: Rematches,
)(implicit ec: scala.concurrent.ExecutionContext) {

  private val declined = new lila.memo.ExpireSetMemo(1 minute)

  private val rateLimit = new lila.memo.RateLimit[String](
    credits = 2,
    duration = 1 minute,
    key = "round.rematch",
  )

  def isOffering(gameId: Game.ID, color: ShogiColor): Boolean =
    rematches isOffering lila.game.PovRef(gameId, color)

  def isOfferingFromPov(pov: Pov): Boolean =
    isOffering(pov.game.id, pov.color)

  def yes(pov: Pov): Fu[Events] = {
    pov match {
      case Pov(game, color) if game.playerCouldRematch =>
        if (isOfferingFromPov(!pov) || game.opponent(color).isAi)
          rematches.getAcceptedId(game.id).fold(rematchJoin(pov))(rematchExists(pov))
        else if (!declined.get(pov.flip.fullId) && rateLimit(pov.fullId)(true)(false))
          rematchCreate(pov)
        else fuccess(List(Event.RematchOffer(by = none)))
      case _ => fuccess(List(Event.ReloadOwner))
    }
  } addEffect { events =>
    pov.game.postGameStudy.foreach(pgs => publishForPostGameStudy(pgs, events))
  }

  def no(pov: Pov): Fu[Events] = {
    if (isOfferingFromPov(pov)) {
      pov.opponent.userId foreach { forId =>
        Bus.publish(lila.hub.actorApi.round.RematchCancel(pov.gameId), s"rematchFor:$forId")
      }
      messenger.systemTrans(pov.game, trans.rematchOfferCanceled)
    } else if (isOfferingFromPov(!pov)) {
      declined put pov.fullId
      messenger.systemTrans(pov.game, trans.rematchOfferDeclined)
    }
    rematches.drop(pov.gameId)
    Bus.publish(
      lila.hub.actorApi.round.RematchChallengeDelete(pov.gameId),
      "rematchChallengeDelete",
    )
    fuccess(List(Event.RematchOffer(by = none)))
  } addEffect { events =>
    pov.game.postGameStudy.foreach(pgs => publishForPostGameStudy(pgs, events))
  }

  def publishForPostGameStudy(studyId: String, events: Events) =
    events.foreach {
      case Event.RematchTaken(gameId) =>
        Bus.publish(lila.hub.actorApi.study.RoundRematch(studyId, gameId), "studyRematch")
      case Event.RematchOffer(by) =>
        Bus.publish(lila.hub.actorApi.study.RoundRematchOffer(studyId, by), "studyRematch")
      case _ =>
    }

  private def rematchExists(pov: Pov)(nextId: Game.ID): Fu[Events] =
    gameRepo game nextId flatMap {
      _.fold(rematchJoin(pov))(g => fuccess(redirectEvents(g)))
    }

  private def rematchJoin(pov: Pov): Fu[Events] = {

    def createGame(withId: Option[Game.ID]) =
      for {
        nextGame <- returnGame(pov, withId) map (_.start)
        _ = rematches.accept(pov.gameId, nextGame.id)
        _ <- gameRepo insertDenormalized nextGame
      } yield {
        // To delete potential challenge rematch
        if (pov.game.isCorrespondence)
          Bus.publish(
            lila.hub.actorApi.round.RematchChallengeDelete(pov.gameId),
            "rematchChallengeDelete",
          )
        messenger.systemTrans(pov.game, trans.rematchOfferAccepted)
        onStart(nextGame.id)
        redirectEvents(nextGame)
      }

    rematches.get(pov.gameId) match {
      case None                           => createGame(none)
      case Some(Rematches.Accepted(id))   => gameRepo game id map { _ ?? redirectEvents }
      case Some(Rematches.Offered(_, id)) => createGame(id.some)
    }
  }

  private def rematchCreate(pov: Pov): Fu[Events] =
    rematches.offer(pov.ref) map { _ =>
      messenger.systemTrans(pov.game, trans.rematchOfferSent)
      pov.opponent.userId foreach { forId =>
        Bus.publish(lila.hub.actorApi.round.RematchOffer(pov.gameId), s"rematchFor:$forId")
      }
      List(Event.RematchOffer(by = pov.color.some))
    }

  private def returnGame(pov: Pov, withId: Option[Game.ID]): Fu[Game] =
    for {
      users <- userRepo byIds pov.game.userIds
      shogiGame = ShogiGame(pov.game.initialSfen, pov.game.variant)
        .copy(clock = pov.game.clock map { c =>
          Clock(c.config)
        })
      sPlayer = returnPlayer(pov.game, Sente, users)
      gPlayer = returnPlayer(pov.game, Gote, users)
      sloppy = Game.make(
        shogi = shogiGame,
        initialSfen = pov.game.initialSfen,
        sentePlayer = if (pov.game.isHandicap) gPlayer else sPlayer,
        gotePlayer = if (pov.game.isHandicap) sPlayer else gPlayer,
        mode = if (users.exists(_.lame)) shogi.Mode.Casual else pov.game.mode,
        proMode = pov.game.isProMode,
        source = pov.game.source | Source.Lobby,
        daysPerTurn = pov.game.daysPerTurn,
        notationImport = None,
      )
      game <- withId.fold(sloppy withUniqueId idGenerator) { id => fuccess(sloppy withId id) }
    } yield game

  private def returnPlayer(game: Game, color: ShogiColor, users: List[User]): lila.game.Player =
    game.opponent(color).engineConfig match {
      case Some(ec) => lila.game.Player.make(color, ec.some)
      case None =>
        lila.game.Player.make(
          color,
          game.opponent(color).userId.flatMap { id =>
            users.find(_.id == id)
          },
          game.perfType,
        )
    }

  def redirectEvents(game: Game): Events = {
    val senteId = game fullIdOf Sente
    val goteId  = game fullIdOf Gote
    List(
      Event.RedirectOwner(
        if (game.isHandicap) Gote else Sente,
        goteId,
        AnonCookie.json(game pov Gote),
      ),
      Event.RedirectOwner(
        if (game.isHandicap) Sente else Gote,
        senteId,
        AnonCookie.json(game pov Sente),
      ),
      // tell spectators about the rematch
      Event.RematchTaken(game.id),
    )
  }

}
