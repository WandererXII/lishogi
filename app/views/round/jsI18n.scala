package views.html.round

import play.api.i18n.Lang

import lila.app.templating.Environment._
import lila.i18n.{ I18nKeys => trans }

object jsI18n {

  def apply(g: lila.game.Game)(implicit lang: Lang) =
    i18nJsObject {
      baseTranslations ++ {
        if (g.isCorrespondence) correspondenceTranslations
        else realtimeTranslations
      } ++ {
        !g.variant.standard ?? variantTranslations
      } ++ {
        g.isTournament ?? tournamentTranslations
      } ++ {
        g.isSwiss ?? swissTranslations
      }
    }

  private val correspondenceTranslations = Vector(
    trans.oneDay,
    trans.nbDays,
    trans.nbHours
  ).map(_.key)

  private val realtimeTranslations = Vector(trans.nbSecondsToPlayTheFirstMove).map(_.key)

  private val variantTranslations = Vector(
    trans.variantEnding
  ).map(_.key)

  private val tournamentTranslations = Vector(
    trans.backToTournament,
    trans.viewTournament,
    trans.standing
  ).map(_.key)

  private val swissTranslations = Vector(
    trans.backToTournament,
    trans.viewTournament
  ).map(_.key)

  private val baseTranslations = Vector(
    trans.black,
    trans.white,
    trans.sente,
    trans.gote,
    trans.shitate,
    trans.uwate,
    trans.flipBoard,
    trans.aiNameLevelAiLevel,
    trans.yourTurn,
    trans.abortGame,
    trans.proposeATakeback,
    trans.offerDraw,
    trans.resign,
    trans.opponentLeftCounter,
    trans.opponentLeftChoices,
    trans.forceResignation,
    trans.forceDraw,
    trans.claimADraw,
    trans.drawOfferSent,
    trans.cancel,
    trans.yourOpponentOffersADraw,
    trans.accept,
    trans.decline,
    trans.takebackPropositionSent,
    trans.yourOpponentProposesATakeback,
    trans.thisAccountViolatedTos,
    trans.gameAborted,
    trans.checkmate,
    trans.xResigned,
    trans.stalemate,
    trans.royalsLost,
    trans.bareKing,
    trans.perpetualCheck,
    trans.xLeftTheGame,
    trans.draw,
    trans.impasse,
    trans.timeOut,
    trans.xIsVictorious,
    trans.withdraw,
    trans.rematch,
    trans.rematchOfferSent,
    trans.rematchOfferAccepted,
    trans.waitingForOpponent,
    trans.cancelRematchOffer,
    trans.newOpponent,
    trans.confirmMove,
    trans.viewRematch,
    trans.xPlays,
    trans.giveNbSeconds,
    trans.preferences.giveMoreTime,
    trans.gameOver,
    trans.analysis,
    trans.postGameStudy,
    trans.standardStudy,
    trans.postGameStudyExplanation,
    trans.studyWithOpponent,
    trans.studyWith,
    trans.optional,
    trans.postGameStudiesOfGame,
    trans.study.createStudy,
    trans.study.searchByUsername,
    trans.yourOpponentWantsToPlayANewGameWithYou,
    trans.youPlayAsX,
    trans.itsYourTurn,
    trans.enteringKing,
    trans.invadingPieces,
    trans.totalImpasseValue,
    trans.fromPosition
  ).map(_.key)
}
