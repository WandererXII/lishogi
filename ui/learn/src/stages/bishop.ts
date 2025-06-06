import { i18n } from 'i18n';
import { obstaclesCaptured } from '../assert';
import type { IncompleteLevel, IncompleteStage } from '../interfaces';
import { createLevel } from '../level';
import { arrow, circle, initial, pieceMovesHighlihts } from '../shapes';
import { toPiece } from '../util';

const levels: IncompleteLevel[] = [
  {
    goal: i18n('learn:grabAllTheStars'),
    sfen: '9/9/9/9/9/9/6B2/9/9 b -',
    nbMoves: 2,
    success: obstaclesCaptured,
    obstacles: ['6d', '8f'],
    drawShapes: initial([arrow('3g', '6d'), arrow('6d', '8f')]),
  },
  {
    goal: i18n('learn:ehThereIsNoPiece'),
    sfen: '9/9/9/9/9/9/9/9/9 b B',
    nbMoves: 7,
    success: obstaclesCaptured,
    obstacles: ['8h', '7i', '7e', '5i', '5g', '4h'],
    drawShapes: initial([circle(toPiece('B')), circle('5e'), arrow('5e', '8h')]),
  },
  {
    goal: i18n('learn:bishopPromotion'),
    sfen: '9/9/9/9/9/9/2B6/9/9 b -',
    nbMoves: 3,
    success: obstaclesCaptured,
    obstacles: ['2b', '3b', '8g'],
  },
  {
    goal: i18n('learn:bishopSummary'),
    sfen: '9/9/9/9/4B4/9/9/9/9 b -',
    nbMoves: 1,
    success: obstaclesCaptured,
    obstacles: ['9i'],
    squareHighlights: initial(pieceMovesHighlihts(toPiece('B'), '5e')),
  },
  {
    goal: i18n('learn:horseSummary'),
    sfen: '9/9/9/9/4+B4/9/9/9/9 b -',
    nbMoves: 2,
    success: obstaclesCaptured,
    obstacles: ['8b', '8c'],
    squareHighlights: initial(pieceMovesHighlihts(toPiece('+B'), '5e')),
  },
];

const stage: IncompleteStage = {
  key: 'bishop',
  title: i18n('learn:theBishop'),
  subtitle: i18n('learn:itMovesDiagonally'),
  intro: i18n('learn:bishopIntro'),
  levels: levels.map((l, i) => createLevel(l, i)),
  complete: i18n('learn:bishopComplete'),
};

export default stage;
