var util = require('./util');
var shogiopsUtil = require('shogiops/util');
var ground = require('./ground');
const timeouts = require('./timeouts');
var m = require('mithril');

module.exports = function (blueprint, opts) {
  var steps = (blueprint || []).map(function (step) {
    if (step.move) return step;
    return {
      move: step,
      shapes: [],
    };
  });

  var it = 0;
  var isFailed = false;

  var fail = function () {
    isFailed = true;
    return false;
  };

  var opponent = function (data) {
    var step = steps[it];
    if (!step) return;
    var res;
    var move = util.decomposeUci(step.move);
    if (step.move[1] === '*') {
      res = opts.shogi.drop(shogiopsUtil.charToRole(move[0][0]), move[1]);
    } else {
      res = opts.shogi.move(move[0], move[1], move[2]);
    }
    if (!res) return fail();
    it++;
    ground.fen(opts.shogi.fen(), opts.shogi.color(), opts.makeShogiDests(), move);
    m.redraw();
    if (step.shapes)
    timeouts.setTimeout(function () {
      ground.setShapes(step.shapes);
    }, 70);

    if (it == steps.length) {
      ground.stop();
      timeouts.setTimeout(data.complete, 500);
    }
  };

  return {
    isComplete: function () {
      return it === steps.length;
    },
    isFailed: function () {
      return isFailed;
    },
    opponent: opponent,
    player: function (data) {
      var move = data.move;
      var step = steps[it];
      if (!step) return;
      if (step.move !== move && !(Array.isArray(step.move) && step.move.includes(move))) return fail();
      it++;
      if (step.shapes) ground.setShapes(step.shapes);
      if (step.levelFail) {
        return step.levelFail
      }
      // example case in setup.js
      if (steps[it] && !steps[it].move) {
        it++;
        opts.shogi.color(shogiopsUtil.opposite(opts.shogi.color()));
        ground.color(opts.shogi.color(), opts.makeShogiDests());
        ground.data().dropmode.dropDests = opts.shogi.getDropDests();
      } else {
        var opponentWrapper = function() {
          opponent(data);
        }
        timeouts.setTimeout(opponentWrapper, steps[it] && steps[it].delay ? steps[it].delay : 500);
      }
      return true;
    },
  };
};
