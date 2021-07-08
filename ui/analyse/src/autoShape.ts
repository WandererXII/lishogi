import { promote } from 'shogiops/util';
import { assureLishogiUsi, parseLishogiUsi, makeChessSquare } from 'shogiops/compat';
import { isDrop, PromotableRole } from 'shogiops/types';
import { winningChances } from 'ceval';
import * as cg from 'shogiground/types';
import { opposite } from 'shogiground/util';
import { DrawShape } from 'shogiground/draw';
import AnalyseCtrl from './ctrl';

function pieceDrop(key: cg.Key, role: cg.Role, color: Color): DrawShape {
  return {
    orig: key,
    piece: {
      color,
      role,
      scale: 0.8,
    },
    brush: 'green',
  };
}

export function makeShapesFromUsi(
  color: Color,
  usi: Usi,
  brush: string,
  pieces?: cg.Pieces,
  modifiers?: any
): DrawShape[] {
  usi = assureLishogiUsi(usi)!;
  const move = parseLishogiUsi(usi)!;
  const to = makeChessSquare(move.to);
  if (isDrop(move)) return [{ orig: to, brush }, pieceDrop(to, move.role, color)];

  const shapes: DrawShape[] = [
    {
      orig: makeChessSquare(move.from),
      dest: to,
      brush,
      modifiers,
    },
  ];
  if (move.promotion && pieces && pieces.get(usi.slice(0, 2) as Key)) {
    const pRole = pieces.get(usi.slice(0, 2) as Key)!.role as PromotableRole;
    shapes.push(pieceDrop(to, promote(pRole), color));
  }
  return shapes;
}

export function compute(ctrl: AnalyseCtrl): DrawShape[] {
  const color = ctrl.node.fen.includes(' w ') ? 'gote' : 'sente';
  const rcolor = opposite(color);
  const pieces = ctrl.shogiground.state.pieces;

  if (ctrl.practice) {
    if (ctrl.practice.hovering()) return makeShapesFromUsi(color, ctrl.practice.hovering().usi, 'paleGreen', pieces);
    const hint = ctrl.practice.hinting();

    if (hint) {
      if (hint.mode === 'move') return makeShapesFromUsi(color, hint.usi, 'paleGreen', pieces);
      else
        return [
          {
            orig: hint.usi[1] === '*' ? hint.usi.slice(2, 4) : hint.usi.slice(0, 2),
            brush: 'paleGreen',
          },
        ];
    }
    return [];
  }
  const instance = ctrl.getCeval();
  const hovering = ctrl.explorer.hovering() || instance.hovering();
  const { eval: nEval = {}, fen: nFen, ceval: nCeval, threat: nThreat } = ctrl.node;

  let shapes: DrawShape[] = [];
  if (ctrl.retro && ctrl.retro.showBadNode()) {
    return makeShapesFromUsi(color, ctrl.retro.showBadNode().usi, 'paleRed', pieces, {
      lineWidth: 8,
    });
  }
  if (hovering && hovering.fen === nFen)
    shapes = shapes.concat(makeShapesFromUsi(color, hovering.usi, 'paleGreen', pieces));
  if (ctrl.showAutoShapes() && ctrl.showComputer()) {
    if (nEval.best) shapes = shapes.concat(makeShapesFromUsi(rcolor, nEval.best, 'paleGreen', pieces));
    if (!hovering) {
      let nextBest = ctrl.nextNodeBest();
      if (!nextBest && instance.enabled() && nCeval) nextBest = nCeval.pvs[0].moves[0];
      if (nextBest) shapes = shapes.concat(makeShapesFromUsi(color, nextBest, 'paleGreen', pieces));
      if (instance.enabled() && nCeval && nCeval.pvs[1] && !(ctrl.threatMode() && nThreat && nThreat.pvs.length > 2)) {
        nCeval.pvs.forEach(function (pv) {
          if (pv.moves[0] === nextBest) return;
          const shift = winningChances.povDiff(color, nCeval.pvs[0], pv);
          if (shift >= 0 && shift < 0.2) {
            shapes = shapes.concat(
              makeShapesFromUsi(color, pv.moves[0], 'paleGreen', pieces, {
                lineWidth: Math.round(12 - shift * 50), // 12 to 2
              })
            );
          }
        });
      }
    }
  }
  if (instance.enabled() && ctrl.threatMode() && nThreat) {
    const [pv0, ...pv1s] = nThreat.pvs;

    shapes = shapes.concat(makeShapesFromUsi(rcolor, pv0.moves[0], pv1s.length > 0 ? 'paleRed' : 'red', pieces));

    pv1s.forEach(function (pv) {
      const shift = winningChances.povDiff(rcolor, pv, pv0);
      if (shift >= 0 && shift < 0.2) {
        shapes = shapes.concat(
          makeShapesFromUsi(rcolor, pv.moves[0], 'paleRed', pieces, {
            lineWidth: Math.round(11 - shift * 45), // 11 to 2
          })
        );
      }
    });
  }
  return shapes;
}
