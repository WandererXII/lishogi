import { opposite } from 'shogiops/util';
import type { Position } from 'shogiops/variant/position';

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
            const clone = pos.clone();
            clone.play({ from: orig, to: dest });
            clone.turn = opposite(clone.turn);
            return clone.isCheck();
          });
        })
      )
        return true;
    }

    // Fail - if opposite check
    const oppositePos = pos.clone();
    oppositePos.turn = opposite(pos.turn);

    if (oppositePos.isCheck()) return true;

    return false;
  } else return false;
}
