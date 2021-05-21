var util = require("../util");
var assert = require("../assert");
var arrow = util.arrow,
  circle = util.circle;

var imgUrl = util.assetUrl + "images/learn/winged-sword.svg";

module.exports = {
  key: "drop",
  title: "pieceDrops",
  subtitle: "reuseCapturedPieces",
  image: imgUrl,
  intro: "dropIntro",
  illustration: util.roundSvg(imgUrl),
  levels: [
    {
      // lance
      goal: "capturedPiecesCanBeDropped",
      fen: "9/9/8r/4n4/9/9/4L4/9/9 b -",
      nbMoves: 3,
      captures: 2,
      shapes: [arrow("e3e6"), circle("h5"), arrow("h5i7")],
      success: assert.extinct("black"),
    },
    {
      // gold
      goal: "takeTheEnemyPiecesAndDontLoseYours",
      fen: "9/9/4nr3/4G4/9/9/9/9/9 b -",
      nbMoves: 2,
      captures: 2,
      success: assert.extinct("black"),
    },
    {
      // bishop
      goal: "takeTheEnemyPiecesAndDontLoseYours",
      fen: "9/9/9/4p4/9/4B1s2/5g3/9/9 b -",
      nbMoves: 4,
      captures: 3,
      success: assert.extinct("black"),
    },
    {
      // knight
      goal: "takeTheEnemyPiecesAndDontLoseYours",
      fen: "9/3spg3/3p1p3/4N4/9/9/9/9/9 b -",
      nbMoves: 5,
      captures: 5,
      success: assert.extinct("black"),
    },
    {
      // rook
      goal: "takeTheEnemyPiecesAndDontLoseYours",
      fen: "9/4r1s2/5n3/3R1b3/9/9/9/9/9 b -",
      nbMoves: 5,
      captures: 4,
      success: assert.extinct("black"),
    },
    {
      // silver
      goal: "takeTheEnemyPiecesAndDontLoseYours",
      fen: "9/5l+p2/4S4/4pp3/9/9/9/9/9 b -",
      nbMoves: 7,
      captures: 4,
      success: assert.extinct("black"),
    },
  ].map(function (l, i) {
    l.pointsForCapture = true;
    l.hasPocket = true;
    return util.toLevel(l, i);
  }),
  complete: "captureComplete",
};
