package lishogi.analyse
package actorApi

import lishogi.game.Game

case class AnalysisReady(game: Game, analysis: Analysis)

case class AnalysisProgress(
    game: Game,
    variant: shogi.variant.Variant,
    initialFen: shogi.format.FEN,
    analysis: Analysis
)

case class StudyAnalysisProgress(analysis: Analysis, complete: Boolean)
