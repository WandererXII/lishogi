import type { Config as SgConfig } from 'shogiground/config';
import { shogigroundDropDests, shogigroundMoveDests, usiToSquareNames } from 'shogiops/compat';
import { makeSfen } from 'shogiops/sfen';
import { handRoles } from 'shogiops/variant/util';
import type { Run } from './interfaces';

export const makeSgOpts = (run: Run, canMove: boolean): SgConfig => {
  const cur = run.current;
  const pos = cur.position();
  const sfen = makeSfen(pos);
  const splitSfen = sfen.split(' ');
  return {
    sfen: { board: splitSfen[0], hands: splitSfen[2] },
    activeColor: run.pov,
    orientation: run.pov,
    turnColor: pos.turn,
    movable: {
      dests: canMove ? shogigroundMoveDests(pos) : undefined,
    },
    droppable: {
      dests: canMove ? shogigroundDropDests(pos) : undefined,
    },
    hands: {
      roles: handRoles('standard'),
    },
    checks: !!pos.isCheck(),
    lastDests: cur.moveIndex > 0 ? usiToSquareNames(cur.lastMove()) : undefined,
  };
};
