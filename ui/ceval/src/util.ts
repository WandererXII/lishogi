import type { Position } from 'shogiops/position/position';
import { opposite } from 'shogiops/util';

export function isEvalBetter(a: Tree.ClientEval, b: Tree.ClientEval): boolean {
  return a.depth > b.depth || (a.depth === b.depth && a.nodes > b.nodes);
}

export function renderEval(e: number): string {
  e = Math.max(Math.min(Math.round(e / 10) / 10, 99), -99);
  return (e > 0 ? '+' : '') + e.toFixed(1);
}

export const unsupportedVariants: VariantKey[] = ['chushogi', 'annanshogi'];

export function invalidPosition(pos: Position): boolean {
  if (pos.rules === 'dobutsu') {
    // Fail - if we are in check and all moves lead to check
    if (pos.isCheck()) {
      const allMoves = pos.allMoveDests();
      if (
        [...allMoves.entries()].every(([orig, dests]) => {
          return [...dests].every(dest => {
            let nextPos = pos.play({ from: orig, to: dest });
            nextPos = nextPos.update({ turn: opposite(nextPos.turn) });
            return nextPos.isCheck();
          });
        })
      )
        return true;
    }

    // Fail - if opposite check
    const oppositePos = pos.update({ turn: opposite(pos.turn) });

    if (oppositePos.isCheck()) return true;

    return false;
  } else return false;
}
