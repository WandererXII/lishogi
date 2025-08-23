import resizeHandle from 'shogi/resize';
import type { Config as SgConfig } from 'shogiground/config';
import type { State } from 'shogiground/state';
import type { Piece, Role } from 'shogiops/types';
import { parseSquareName } from 'shogiops/util';
import {
  handRoles,
  pieceCanPromote,
  pieceForcePromote,
  promotableOnDrop,
  promote,
} from 'shogiops/variant/util';
import type { PuzPrefs, UserDrop, UserMove } from '../interfaces';

// all puzzles are standard
const variant: VariantKey = 'standard';

export function makeConfig(
  opts: SgConfig,
  pref: PuzPrefs,
  userMove: UserMove,
  userDrop: UserDrop,
  state: State,
): SgConfig {
  return {
    sfen: opts.sfen,
    activeColor: opts.activeColor,
    orientation: opts.orientation,
    turnColor: opts.turnColor,
    checks: opts.checks,
    lastDests: opts.lastDests,
    coordinates: { enabled: pref.coords !== 0 },
    hands: {
      roles: handRoles(variant),
      inlined: true,
    },
    movable: {
      free: false,
      dests: opts.movable!.dests,
      showDests: pref.destination,
    },
    droppable: {
      free: false,
      dests: opts.droppable!.dests,
      showDests: pref.destination && pref.dropDestination,
    },
    promotion: {
      promotesTo: (role: Role) => promote(variant)(role),
      movePromotionDialog: (orig: Key, dest: Key) => {
        const piece = state.pieces.get(orig) as Piece;
        const capture = state.pieces.get(dest) as Piece | undefined;
        return (
          !!piece &&
          pieceCanPromote(variant)(
            piece,
            parseSquareName(orig)!,
            parseSquareName(dest)!,
            capture,
          ) &&
          !pieceForcePromote(variant)(piece, parseSquareName(dest)!)
        );
      },
      dropPromotionDialog(piece) {
        return promotableOnDrop(variant)(piece as Piece);
      },
      forceMovePromotion: (orig: Key, dest: Key) => {
        const piece = state.pieces.get(orig) as Piece;
        return !!piece && pieceForcePromote(variant)(piece, parseSquareName(dest)!);
      },
    },
    draggable: {
      enabled: pref.moveEvent > 0,
      showGhost: pref.highlightLastDests,
      showTouchSquareOverlay: pref.squareOverlay,
    },
    selectable: {
      enabled: pref.moveEvent !== 1,
    },
    events: {
      move: userMove,
      drop: userDrop,
      insert(elements) {
        if (elements) resizeHandle(elements, pref.resizeHandler, { visible: () => true });
      },
    },
    premovable: {
      enabled: false,
    },
    predroppable: {
      enabled: false,
    },
    drawable: {
      enabled: true,
    },
    highlight: {
      lastDests: pref.highlightLastDests,
      check: pref.highlightCheck,
    },
    animation: {
      enabled: false,
    },
  };
}
