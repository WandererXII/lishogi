import { i18n } from 'i18n';
import { check, not } from '../assert';
import type { IncompleteLevel, IncompleteStage } from '../interfaces';
import { createLevel } from '../level';
import { arrow, checkShapes, concat, initial } from '../shapes';

const levels: IncompleteLevel[] = [
  {
    goal: i18n('learn:checkInOneGoal'),
    sfen: '5R3/4Ssk2/5p3/9/7L1/9/9/9/9 b r2b4g2s4n3l17p 3',
    nbMoves: 1,
    success: check('gote'),
    failure: not(check('gote')),
    drawShapes: concat(initial([arrow('4a', '2a')]), checkShapes),
  },
  {
    goal: i18n('learn:checkInOneGoal'),
    sfen: '3g5/2rk1g3/pps2pppp/2S6/9/9/9/9/9 b G 1',
    nbMoves: 1,
    success: check('gote'),
    failure: not(check('gote')),
    drawShapes: checkShapes,
  },
  {
    goal: i18n('learn:checkInOneGoal'),
    sfen: '9/9/9/2B2r3/9/4L4/9/9/7k1 b - 1',
    nbMoves: 1,
    success: check('gote'),
    failure: not(check('gote')),
    drawShapes: checkShapes,
  },
  {
    goal: i18n('learn:checkInOneGoal'),
    sfen: '5+B2s/7k1/7N1/6Ss1/9/9/9/9/9 b r2b4g2s4n3l17p 1',
    nbMoves: 1,
    success: check('gote'),
    failure: not(check('gote')),
  },
  {
    goal: i18n('learn:checkInOneGoal'),
    sfen: '6snl/4+Rgk2/5pppp/9/9/9/9/9/9 b GSr2b2g2s3n3l14p 1',
    nbMoves: 1,
    success: check('gote'),
    failure: not(check('gote')),
  },
];

const stage: IncompleteStage = {
  key: 'check1',
  title: i18n('learn:checkInOne'),
  subtitle: i18n('learn:attackTheOpponentsKing'),
  intro: i18n('learn:checkInOneIntro'),
  levels: levels.map((l, i) => createLevel(l, i)),
  complete: i18n('learn:checkInOneComplete'),
};
export default stage;
