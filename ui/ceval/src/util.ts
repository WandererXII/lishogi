import { kingAttacks } from 'shogiops/attacks';
import { defined } from 'shogiops/util';
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
    const kingSente = pos.kingsOf('sente').singleSquare();
    const kingGote = pos.kingsOf('gote').singleSquare();

    if (defined(kingSente) && defined(kingGote)) {
      const kingsTouching =
        kingAttacks(kingSente).has(kingGote) || kingAttacks(kingGote).has(kingSente);
      if (kingsTouching) return true;
    }

    return false;
  } else return false;
}
