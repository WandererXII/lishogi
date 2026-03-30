package lila.round

import scala.concurrent.duration._

import com.github.blemale.scaffeine.Cache

import lila.game.Event
import lila.game.Game
import lila.game.GameRepo
import lila.game.Pov
import lila.game.Progress
import lila.i18n.{ I18nKeys => trans }
import lila.memo.CacheApi

final private[round] class Pauser(
    messenger: Messenger,
    gameRepo: GameRepo,
)(implicit ec: scala.concurrent.ExecutionContext) {

  private val rateLimit = new lila.memo.RateLimit[String](
    credits = 2,
    duration = 15 minute,
    key = "round.pauser",
  )

  def yes(pov: Pov)(implicit proxy: GameProxy): Fu[Events] =
    pov match {
      case Pov(g, color) if pov.opponent.isOfferingPause && !g.prePaused =>
        proxy.save {
          messenger.systemWithTimestamp(
            g,
            trans.adjournmentOfferAccepted,
          )
          Progress(g, g.updatePlayers(_.offerPause))
        } inject List(Event.PauseOffer(by = color.some))
      case Pov(g, color) if g.playerCanOfferPause(color) && rateLimit(pov.fullId)(true)(false) =>
        proxy.save {
          messenger.systemWithTimestamp(
            g,
            trans.xOffersAdjournment,
            (color, g.isHandicap).some,
          )
          Progress(g, g.updatePlayer(color, _.offerPause))
        } inject List(Event.PauseOffer(by = color.some))
      case _ => fuccess(List(Event.ReloadOwner))
    }

  def no(pov: Pov)(implicit proxy: GameProxy): Fu[Events] =
    pov match {
      case Pov(g, color) if pov.player.isOfferingPause =>
        proxy.save {
          messenger.systemWithTimestamp(g, trans.adjournmentOfferCanceled)
          Progress(g, g.updatePlayer(color, _.removePauseOffer))
        } inject List(Event.PauseOffer(by = none))
      case Pov(g, color) if pov.opponent.isOfferingPause =>
        proxy.save {
          messenger.systemWithTimestamp(g, trans.xDeclinesAdjournment, (color, g.isHandicap).some)
          Progress(g, g.updatePlayer(!color, _.removePauseOffer))
        } inject List(Event.PauseOffer(by = none))
      case _ => fuccess(List(Event.ReloadOwner))
    }

  private val resumeOffers: Cache[Game.ID, Pauser.ResumeOffers] = CacheApi.scaffeineNoScheduler
    .expireAfterWrite(3 minutes)
    .build[Game.ID, Pauser.ResumeOffers]()

  def isOfferingResume(gameId: Game.ID, color: shogi.Color): Boolean =
    resumeOffers.getIfPresent(gameId).exists(_(color))

  private def isOfferingResumeFromPov(pov: Pov): Boolean =
    isOfferingResume(pov.gameId, pov.color)

  def resumeYes(
      pov: Pov,
  )(implicit proxy: GameProxy): Fu[Either[Events, (shogi.format.usi.Usi, Progress)]] =
    pov match {
      case Pov(g, color) if g.paused =>
        if (isOfferingResume(g.id, !color)) {
          resumeOffers invalidate g.id
          messenger.systemTrans(g, trans.gameResumed)
          val prog = Progress(g, g.resume, List(Event.Reload))
          proxy.save(prog) >>
            gameRepo.resume(
              prog.game.id,
              prog.game.pausedSeconds,
              prog.game.userIds.distinct,
              prog.game.clock
                .filter(_.isRunning)
                .fold(60)(clk => (clk.estimateTotalSeconds / 60) atLeast 1),
            ) inject (
              prog.game.usis.lastOption
                .filter(usi => Some(usi) == g.sealedUsi && g.plies < prog.game.plies)
                .fold {
                  if (g.sealedUsi.isDefined)
                    messenger.system(g, "Couldn't play sealed move") // should never happen
                  Left(prog.events)
                    .withRight[(shogi.format.usi.Usi, Progress)]
                } { usi =>
                  Right((usi, prog)).withLeft[Events]
                }
            )
        } else if (!isOfferingResume(g.id, color)) {
          resumeOffers.put(g.id, Pauser.ResumeOffers(sente = color.sente, gote = color.gote))
          messenger.systemTrans(g, trans.xOffersResumption, (color, g.isHandicap).some)
          fuccess(Left(List(Event.ResumeOffer(by = color.some))))
        } else fuccess(Left(List(Event.ReloadOwner)))
      case _ => fuccess(Left(List(Event.ReloadOwner)))
    }

  def resumeNo(pov: Pov): Fu[Events] =
    if (pov.game.paused) {
      if (isOfferingResumeFromPov(pov)) {
        messenger.systemTrans(pov.game, trans.resumptionOfferCanceled)
      } else if (isOfferingResumeFromPov(!pov)) {
        messenger.systemTrans(
          pov.game,
          trans.xDeclinesResumption,
          (pov.color, pov.game.isHandicap).some,
        )
      }
      resumeOffers invalidate pov.gameId
      fuccess(List(Event.ResumeOffer(by = none)))
    } else fuccess(List(Event.ReloadOwner))
}

private object Pauser {

  case class ResumeOffers(sente: Boolean, gote: Boolean) {
    def apply(color: shogi.Color) = color.fold(sente, gote)
  }
}
