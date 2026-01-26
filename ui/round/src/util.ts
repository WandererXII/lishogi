import type * as sg from 'shogiground/types';
import {
  shogigroundDropDests,
  shogigroundMoveDests,
  squareSetToSquareNames,
} from 'shogiops/compat';
import type { SquareName } from 'shogiops/types';
import { makePieceName, makeSquareName, type Result } from 'shogiops/util';
import type { Position } from 'shogiops/variant/position';
import { fullSquareSet, handRoles } from 'shogiops/variant/util';
import type { VNodeData } from 'snabbdom';

export { bind, onInsert } from 'common/snabbdom';

export function justIcon(icon: string): VNodeData {
  return {
    attrs: { 'data-icon': icon },
  };
}
export function getMoveDests(posRes: Result<Position>, illegal: boolean): sg.MoveDests {
  return posRes.unwrap(
    p => (illegal ? moveDestsWithIllegal(p) : shogigroundMoveDests(p)),
    _ => new Map(),
  );
}

export function getDropDests(posRes: Result<Position>, illegal: boolean): sg.DropDests {
  return posRes.unwrap(
    p => (illegal ? allDropDests(p) : shogigroundDropDests(p)),
    _ => new Map(),
  );
}

// from shogiops/src/compat.ts, with no king in ctx
function moveDestsWithIllegal(pos: Position): sg.MoveDests {
  const result: Map<SquareName, SquareName[]> = new Map();
  const ctx = pos.ctx();
  ctx.king = undefined;

  for (const [from, squares] of pos.allMoveDests(ctx)) {
    if (squares.nonEmpty()) {
      const d = squareSetToSquareNames(squares);
      result.set(makeSquareName(from), d);
    }
  }
  return result;
}

function allDropDests(pos: Position): sg.DropDests {
  const allButMine = squareSetToSquareNames(
    fullSquareSet(pos.rules).diff(pos.board.color(pos.turn)),
  );
  const result: sg.DropDests = new Map();

  for (const role of handRoles(pos.rules)) {
    const piece = { color: pos.turn, role };
    if (pos.hands[pos.turn].get(role) > 0) {
      result.set(makePieceName(piece), allButMine);
    } else result.set(makePieceName(piece), []);
  }

  return result;
}
