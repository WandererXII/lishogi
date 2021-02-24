var util = require('../util');
var assert = require('../assert');
var arrow = util.arrow;

var imgUrl = util.assetUrl + 'images/learn/bowman.svg';

module.exports = {
  key: 'capture',
  title: 'capture',
  subtitle: 'takeTheEnemyPieces',
  image: imgUrl,
  intro: 'captureIntro',
  illustration: util.roundSvg(imgUrl),
  levels: [
    {
      // lance
      goal: 'takeTheEnemyPieces',
      fen: '9/4n4/9/9/4p4/9/9/4L4/9 b -',
      nbMoves: 2,
      captures: 2,
      shapes: [arrow('e2e5'), arrow('e5e8')],
      success: assert.extinct('white'),
    },
    {
      // gold
      goal: 'takeTheEnemyPiecesAndDontLoseYours',
      fen: '9/9/4nr3/4G4/9/9/9/9/9 b -',
      nbMoves: 2,
      captures: 2,
      success: assert.extinct('white'),
    },
    {
      // bishop
      goal: 'takeTheEnemyPiecesAndDontLoseYours',
      fen: '8/5r2/8/1r3p2/8/3B4/8/8 b -',
      nbMoves: 5,
      captures: 3,
      success: assert.extinct('white'),
    },
    {
      // queen
      goal: 'takeTheEnemyPiecesAndDontLoseYours',
      fen: '8/5b2/5p2/3n2p1/8/6Q1/8/8 b -',
      nbMoves: 7,
      captures: 4,
      success: assert.extinct('white'),
    },
    {
      // knight
      goal: 'takeTheEnemyPiecesAndDontLoseYours',
      fen: '8/3b4/2p2q2/8/3p1N2/8/8/8 b -',
      nbMoves: 6,
      captures: 4,
      success: assert.extinct('white'),
    },
  ].map(function (l, i) {
    l.pointsForCapture = true;
    return util.toLevel(l, i);
  }),
  complete: 'captureComplete',
};
