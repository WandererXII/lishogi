package lila.round

import play.api.i18n.Lang

import lila.common.Bus
import lila.game.Event
import lila.game.Game
import lila.game.Pov
import lila.game.Progress
import lila.i18n.defaultLang
import lila.i18n.{ I18nKeys => trans }

final private[round] class Drawer(
    messenger: Messenger,
    finisher: Finisher,
)(implicit ec: scala.concurrent.ExecutionContext) {

  implicit private val chatLang: Lang = defaultLang

  def yes(pov: Pov)(implicit proxy: GameProxy): Fu[Events] = pov.game.drawable ?? {
    pov match {
      case pov if pov.opponent.isOfferingDraw =>
        finisher.other(pov.game, _.Draw, None, Some(trans.drawOfferAccepted.txt()))
      case Pov(g, color) if g playerCanOfferDraw color =>
        val progress = Progress(g) map { g =>
          g.updatePlayer(color, _ offerDraw g.plies)
        }
        messenger.system(g, trans.xOffersDraw, color.toString)
        proxy.save(progress) >>-
          publishDrawOffer(progress.game) inject
          List(Event.DrawOffer(by = color.some))
      case _ => fuccess(List(Event.ReloadOwner))
    }
  }

  def no(pov: Pov)(implicit proxy: GameProxy): Fu[Events] = pov.game.drawable ?? {
    pov match {
      case Pov(g, color) if pov.player.isOfferingDraw =>
        proxy.save {
          messenger.system(g, trans.drawOfferCanceled)
          Progress(g) map { g =>
            g.updatePlayer(color, _.removeDrawOffer)
          }
        } inject List(Event.DrawOffer(by = none))
      case Pov(g, color) if pov.opponent.isOfferingDraw =>
        proxy.save {
          messenger.system(g, trans.xDeclinesDraw, color.toString)
          Progress(g) map { g =>
            g.updatePlayer(!color, _.removeDrawOffer)
          }
        } inject List(Event.DrawOffer(by = none))
      case _ => fuccess(List(Event.ReloadOwner))
    }
  }

  def claim(pov: Pov)(implicit proxy: GameProxy): Fu[Events] =
    (pov.game.playable && pov.game.history.fourfoldRepetition) ?? finisher.other(
      pov.game,
      _.Draw,
      None,
    )

  def force(game: Game)(implicit proxy: GameProxy): Fu[Events] =
    finisher.other(game, _.Draw, None, None)

  private def publishDrawOffer(game: Game): Unit = if (game.nonAi) {
    if (game.isCorrespondence)
      Bus.publish(
        lila.hub.actorApi.round.CorresDrawOfferEvent(game.id),
        "offerEventCorres",
      )
    if (lila.game.Game.isBoardOrBotCompatible(game))
      Bus.publish(
        lila.game.actorApi.BoardDrawOffer(game),
        lila.game.actorApi.BoardDrawOffer makeChan game.id,
      )
  }
}
