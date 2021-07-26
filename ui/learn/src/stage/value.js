var util = require('../util');
var assert = require('../assert');
var arrow = util.arrow;

var imgUrl = util.assetUrl + 'images/learn/sprint.svg';

module.exports = {
  key: 'value',
  title: 'pieceValue',
  subtitle: 'evaluatePieceStrength',
  image: imgUrl,
  intro: 'pieceValueIntro',
  illustration: util.roundSvg(imgUrl),
  levels: [
    {
      goal: 'takeThePieceWithTheHighestValue',
      fen: '9/9/4lp3/4+B4/9/6n2/9/9/9 b - 1',
      scenario: ['e6g4'],
      nbMoves: 1,
      captures: 1,
      success: assert.scenarioComplete,
      failure: assert.scenarioFailed,
      detectCapture: true,
    },
    {
      goal: 'takeThePieceWithTheHighestValue',
      fen: '9/9/4ns3/4S4/3g5/9/9/9/9 b - 1',
      scenario: ['e6f7+'],
      nbMoves: 1,
      captures: 1,
      success: assert.scenarioComplete,
      failure: assert.scenarioFailed,
      detectCapture: true,
    },
    {
      goal: 'takeThePieceWithTheHighestValue',
      fen: '9/9/9/2rl5/2BG5/1N1Kb4/2g6/9/9 b - 1',
      scenario: ['b4c6'],
      nbMoves: 1,
      captures: 1,
      offerIllegalMove: true,
      success: assert.scenarioComplete,
      failure: assert.scenarioFailed,
      detectCapture: true,
    },
    {
      goal: 'takeThePieceWithTheHighestValue',
      fen: '9/9/9/3lg4/9/3+R1n3/2r1bp3/2s6/9 b - 1',
      nbMoves: 11,
      captures: 7,
      offerIllegalMove: true,
      success: assert.extinct('white'),
      detectCapture: true,
      capturePiecesInOrderOfValue: true,
    },
  ].map(function (l, i) {
    l.pointsForCapture = true;
    return util.toLevel(l, i);
  }),
  complete: 'pieceValueComplete',
};
