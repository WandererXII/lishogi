var Shogi = require('shogiops').Shogi;
var util = require('shogiops/util');
var fenUtil = require('shogiops/fen');
var compat = require('shogiops/compat');
var squareSet = require('shogiops/squareSet');

module.exports = function (fen, appleKeys) {
  if (fen.split(' ').length === 1) fen += ' b';
  var shogi = Shogi.fromSetup(fenUtil.parseFen(fen).unwrap(), false).unwrap();

  // adds enemy pawns on apples, for collisions
  if (appleKeys) {
    appleKeys.forEach(function (key) {
      shogi.board.set(compat.parseChessSquare(key), {
        role: 'pawn',
        color: util.opposite(shogi.turn),
      });
    });
  }

  function placePiece(role, color, key) {
    shogi.board.set(compat.parseChessSquare(key), { role: role, color: color });
  }

  function getColor() {
    return shogi.turn;
  }

  function setColor(c) {
    shogi.turn = c;
  }

  function findKing(c) {
    return compat.makeChessSquare(shogi.board.kingOf(c));
  }

  var findCaptures = function () {
    var allCaptures = [];
    for (const [o, d] of shogi.allDests()) {
      for (const s of d) {
        if (shogi.board[util.opposite(shogi.turn)].has(s))
          allCaptures.push({
            orig: compat.makeChessSquare(o),
            dest: compat.makeChessSquare(s),
          });
      }
    }
    return allCaptures;
  };

  // This might be moved to shogiops later
  var illegalMoves = function () {
    const result = new Map();
    const illegalDests = shogi.allDests({
      king: undefined,
      blockers: squareSet.SquareSet.empty(),
      checkers: squareSet.SquareSet.empty(),
      variantEnd: false,
      mustCapture: false,
    });
    for (const [from, squares] of illegalDests) {
      if (squares.nonEmpty()) {
        const d = Array.from(squares, s => compat.makeChessSquare(s));
        result.set(compat.makeChessSquare(from), d);
      }
    }
    return result;
  };

  return {
    dests: function (opts) {
      opts = opts || {};
      if (!Object.fromEntries) {
        Object.fromEntries = function (entries) {
          if (!entries || !entries[Symbol.iterator]) {
            throw new Error('Object.fromEntries() requires a single iterable argument');
          }
          let obj = {};
          for (let [key, value] of entries) {
            obj[key] = value;
          }
          return obj;
        };
      }
      if (opts.illegal) return Object.fromEntries(illegalMoves());
      return Object.fromEntries(compat.shogigroundDests(shogi));
    },
    pockets: function () {
      return shogi.pockets;
    },
    color: function (c) {
      if (c) setColor(c);
      else return getColor();
    },
    fen: function () {
      return fenUtil.makeFen(shogi.toSetup());
    },
    move: function (orig, dest, prom) {
      var capturedPiece = shogi.board.get(compat.parseChessSquare(dest));
      shogi.play({
        from: compat.parseChessSquare(orig),
        to: compat.parseChessSquare(dest),
        promotion: prom
      });
      return { from: orig, to: dest, promotion: prom, captured: capturedPiece };
    },
    drop: function(role, dest) {
      shogi.play({
        role: role,
        to: compat.parseChessSquare(dest)
      });
      return { from: 'a0', to: dest };
    },
    getDropDests: function () {
      return compat.shogigroundDropDests(shogi);
    },
    getDropDestsIgnoreChecksAndNifu: function (c) {
      var clone = shogi.clone();
      var kingSquare = clone.board.kingOf(c);
      // change king to gold, there is no king in check if the king doesn't exist :)
      clone.board.take(kingSquare);
      console.log(clone);
      clone.board.set(kingSquare, {'role': 'gold', 'color': 'gote'});
      // change pawns to golds
      var pawnSquareSet = clone.board.pieces(c, 'pawn');
      clone.board['pawn'] = clone.board['pawn'].diff(pawnSquareSet);
      clone.board['gold'] = clone.board['gold'].union(pawnSquareSet);
      return compat.shogigroundDropDests(clone);
    },
    occupation: function () {
      return shogi.board;
    },
    kingKey: function (color) {
      return findKing(color);
    },
    findCapture: function () {
      return findCaptures()[0];
    },
    findUnprotectedCapture: function () {
      return findCaptures().find(function (capture) {
        const clone = shogi.clone();
        clone.play({
          from: compat.parseChessSquare(capture.orig),
          to: compat.parseChessSquare(capture.dest),
          promotion: capture.promotion,
        });
        for (const [_, d] of clone.allDests()) {
          if (d.has(compat.parseChessSquare(capture.dest))) return false;
        }
        return true;
      });
    },
    // handles c = undefined, sente or gote
    isCheck: function (c) {
      let isCurrCheck = false,
        isCloneCheck = false;
      if (util.opposite(shogi.turn) !== c) {
        isCurrCheck = shogi.isCheck();
      }
      if (shogi.turn !== c) {
        const clone = shogi.clone();
        clone.turn = util.opposite(clone.turn);

        isCloneCheck = clone.isCheck();
      }
      return isCurrCheck || isCloneCheck;
    },
    checks: function () {
      const clone = shogi.clone();
      clone.turn = util.opposite(clone.turn);
      const colorInCheck = shogi.isCheck() ? shogi.turn : (clone.isCheck() ? clone.turn : undefined);
      const checkingColor = util.opposite(colorInCheck);
      if (colorInCheck === undefined) return null;
      const kingPos = this.kingKey(colorInCheck);

      clone.turn = checkingColor;
      const allDests = clone.allDests();
      const origOfCheck = [];
      for (const k of allDests.keys()) {
        if (allDests.get(k).has(compat.parseChessSquare(kingPos))) origOfCheck.push(k);
      }
      const checks = origOfCheck.map(s => {
        return {
          orig: compat.makeChessSquare(s),
          dest: kingPos,
        };
      });
      return checks;
    },
    playRandomMove: function () {
      const allD = shogi.allDests();
      const keys = Array.from(allD.keys());
      const from = keys[Math.floor(Math.random() * keys.length)];
      // first() is not really random but good enough
      const to = allD.get(from).first();
      shogi.play({ from: from, to: to });
      return { orig: from, dest: to };
    },
    place: placePiece,
    instance: shogi,
  };
};
