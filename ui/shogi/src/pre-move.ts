import type { Pieces } from 'shogiground/types';
import { attacks, kingAttacks } from 'shogiops/attacks';
import { SquareSet } from 'shogiops/square-set';
import type { Piece } from 'shogiops/types';
import { makeSquareName, parseSquareName } from 'shogiops/util';
import { directlyBehind } from 'shogiops/variant/annanshogi';
import { fullSquareSet } from 'shogiops/variant/util';

export function premove(variant: VariantKey): (key: Key, pieces: Pieces) => Key[] {
  return (key, pieces) => {
    const piece = pieces.get(key) as Piece | undefined;
    if (piece) {
      const attackingPiece: Piece =
        variant === 'annanshogi'
          ? ((pieces.get(makeSquareName(directlyBehind(piece.color, parseSquareName(key)))) ??
              piece) as Piece)
          : piece;
      const keySquare = parseSquareName(key);
      return Array.from(
        attacks(attackingPiece, keySquare, SquareSet.empty()).intersect(
          variant === 'dobutsu'
            ? kingAttacks(keySquare).intersect(fullSquareSet(variant))
            : fullSquareSet(variant),
        ),
        s => makeSquareName(s),
      );
    } else return [];
  };
}
