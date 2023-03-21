package views.html.board

import play.api.i18n.Lang

import lila.app.templating.Environment._
import lila.i18n.{ I18nKeys => trans, MessageKey }

object userAnalysisI18n {

  def apply(
      withCeval: Boolean = true,
      withExplorer: Boolean = true,
      withForecast: Boolean = false,
      withAdvantageChart: Boolean = false
  )(implicit lang: Lang) =
    i18nJsObject(
      baseTranslations ++ {
        withCeval ?? cevalTranslations
      } ++ {
        withExplorer ?? explorerTranslations
      } ++ {
        withForecast ?? forecastTranslations
      } ++ {
        withAdvantageChart ?? advantageChartTranslations
      }
    )

  private val baseTranslations: Vector[MessageKey] = Vector(
    trans.black,
    trans.white,
    trans.sente,
    trans.gote,
    trans.shitate,
    trans.uwate,
    trans.analysis,
    trans.flipBoard,
    trans.backToGame,
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
    trans.playingRightNow,
    trans.xIsVictorious,
    trans.cheatDetected,
    trans.variantEnding,
    trans.xPlays,
    trans.gameOver,
    trans.importKif,
    trans.importCsa,
    trans.requestAComputerAnalysis,
    trans.computerAnalysis,
    trans.learnFromYourMistakes,
    trans.averageCentipawnLoss,
    trans.inaccuracies,
    trans.mistakes,
    trans.blunders,
    trans.goodMove,
    trans.viewTheSolution,
    trans.spectators,
    trans.fromPosition,
    // action menu
    trans.menu,
    trans.boardEditor,
    trans.continueFromHere,
    trans.toStudy,
    trans.playWithTheMachine,
    trans.playWithAFriend,
    trans.openStudy,
    trans.preferences.preferences,
    trans.inlineNotation,
    trans.replayMode,
    trans.slow,
    trans.fast,
    trans.realtimeReplay,
    trans.byCPL,
    // context menu
    trans.promoteVariation,
    trans.makeMainLine,
    trans.deleteFromHere,
    trans.forceVariation,
    // practice (also uses checkmate, draw)
    trans.practiceWithComputer,
    trans.goodMove,
    trans.inaccuracy,
    trans.mistake,
    trans.blunder,
    trans.anotherWasX,
    trans.bestWasX,
    trans.youBrowsedAway,
    trans.resumePractice,
    trans.xWinsGame,
    trans.theGameIsADraw,
    trans.yourTurn,
    trans.computerThinking,
    trans.seeBestMove,
    trans.hideBestMove,
    trans.getAHint,
    trans.evaluatingYourMove,
    // gamebook
    trans.puzzle.findTheBestMoveForX
  ).map(_.key)

  private val cevalTranslations: Vector[MessageKey] = Vector(
    // also uses gameOver
    trans.depthX,
    trans.usingServerAnalysis,
    trans.loadingEngine,
    trans.cloudAnalysis,
    trans.goDeeper,
    trans.showThreat,
    trans.inLocalBrowser,
    trans.toggleLocalEvaluation,
    // ceval menu
    trans.computerAnalysis,
    trans.enable,
    trans.bestMoveArrow,
    trans.evaluationGauge,
    trans.infiniteAnalysis,
    trans.removesTheDepthLimit,
    trans.multipleLines,
    trans.cpus,
    trans.memory
  ).map(_.key)

  private val explorerTranslations: Vector[MessageKey] = Vector(
    // also uses gameOver, checkmate, stalemate, draw, variantEnding
    trans.openingExplorerAndTablebase,
    trans.openingExplorer,
    trans.xOpeningExplorer,
    trans.move,
    trans.games,
    trans.variantLoss,
    trans.variantWin,
    trans.insufficientMaterial,
    trans.capture,
    trans.pawnMove,
    trans.close,
    trans.winning,
    trans.unknown,
    trans.losing,
    trans.drawn,
    trans.timeControl,
    trans.averageElo,
    trans.database,
    trans.recentGames,
    trans.topGames,
    trans.averageRatingX,
    trans.noGameFound,
    trans.maybeIncludeMoreGamesFromThePreferencesMenu,
    trans.allSet
  ).map(_.key)

  private val forecastTranslations: Vector[MessageKey] = Vector(
    trans.conditionalPremoves,
    trans.addCurrentVariation,
    trans.playVariationToCreateConditionalPremoves,
    trans.noConditionalPremoves,
    trans.playX,
    trans.andSaveNbPremoveLines
  ).map(_.key)

  private val advantageChartTranslations: Vector[MessageKey] = Vector(
    trans.advantage,
    trans.opening,
    trans.middlegame,
    trans.endgame
  ).map(_.key)
}
