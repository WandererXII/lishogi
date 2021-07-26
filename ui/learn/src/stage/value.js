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
      pointsForCapture: true,
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
      pointsForCapture: true,
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
      pointsForCapture: true,
    },
    {
      goal: 'takeThePieceWithTheHighestValue',
      fen: '9/9/9/3lg4/9/3+R1n3/2r1bp3/2s6/9 b - 1',
      nbMoves: 9,
      captures: 7,
      offerIllegalMove: true,
      success: assert.extinct('white'),
      detectCapture: true,
      capturePiecesInOrderOfValue: true,
      pointsForCapture: true,
    },
    {
      goal: 'takeThePieceWithTheHighestValue',
      fen: '9/6k2/3p2g2/3s5/7N1/9/9/3R5/9 b - 1',
      scenario: [['h5g7+', 'h5g7'], 'g8g7'],
      nbMoves: 1,
      captures: 1,
      offerIllegalMove: true,
      success: assert.scenarioComplete,
      failure: assert.scenarioFailed,
    },
    {
      goal: 'takeThePieceWithTheHighestValue',
      fen: '7k1/9/6+P+P+P/9/P3n4/2N6/2P6/2K+r5/L8 b 2g 1',
      scenario: [
        {
          move: 'c2b3',
          wrongMoves: [
            ['c2d2', 'g*d3', 'd2c1', 'g*c2'],
            ['c2b1', 'g*c1'],
          ]
        }
      ],
      offerIllegalMove: true,
      success: assert.scenarioComplete,
      failure: assert.scenarioFailed,
    },
  ].map(function (l, i) {
    return util.toLevel(l, i);
  }),
  complete: 'pieceValueComplete',
};
