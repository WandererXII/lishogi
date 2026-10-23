import { prefs } from 'common/prefs';
import { promote } from 'common/shogiops';
import { notationFiles, notationRanks } from 'shogi/notation';
import resizeHandle from 'shogi/resize';
import type { Config as SgConfig } from 'shogiground/config';
import { pieceCanPromote, pieceForcePromote } from 'shogiops/position/util';
import type { Piece } from 'shogiops/types';
import { parseSquareName } from 'shogiops/util';
import { h, type VNode } from 'snabbdom';
import type { Controller } from '../interfaces';

export function renderBoard(ctrl: Controller): VNode {
  return h('div.sg-wrap', {
    hook: {
      insert: vnode => {
        ctrl.shogiground.set(makeConfig(ctrl));
        ctrl.shogiground.attach({ board: vnode.elm as HTMLElement });
      },
    },
  });
}

const standardPieceCanPromote = pieceCanPromote('standard');
const standardPieceForcePromote = pieceForcePromote('standard');

function makeConfig(ctrl: Controller): SgConfig {
  const opts = ctrl.makeSgOpts();
  return {
    sfen: opts.sfen,
    orientation: opts.orientation,
    turnColor: opts.turnColor,
    activeColor: opts.activeColor,
    checks: opts.checks,
    lastDests: opts.lastDests,
    coordinates: {
      enabled: ctrl.pref.coords !== prefs.coords.NONE,
      files: notationFiles(),
      ranks: notationRanks(),
    },
    movable: {
      free: false,
      dests: opts.movable!.dests,
      showDests: ctrl.pref.destination,
    },
    droppable: {
      free: false,
      dests: opts.droppable!.dests,
      showDests: ctrl.pref.destination && ctrl.pref.dropDestination,
    },
    promotion: {
      promotesTo: promote('standard'),
      movePromotionDialog: (orig: Key, dest: Key) => {
        const piece = ctrl.shogiground.state.pieces.get(orig) as Piece;
        return (
          !!piece &&
          standardPieceCanPromote(
            piece,
            parseSquareName(orig)!,
            parseSquareName(dest)!,
            undefined,
          ) &&
          !standardPieceForcePromote(piece, parseSquareName(dest)!)
        );
      },
      forceMovePromotion: (orig: Key, dest: Key) => {
        const piece = ctrl.shogiground.state.pieces.get(orig) as Piece;
        return !!piece && standardPieceForcePromote(piece, parseSquareName(dest)!);
      },
    },
    draggable: {
      enabled: ctrl.pref.moveEvent > prefs.moveEvent.CLICK,
      showGhost: ctrl.pref.highlightLastDests,
      showTouchSquareOverlay: ctrl.pref.squareOverlay,
    },
    selectable: {
      enabled: ctrl.pref.moveEvent !== prefs.moveEvent.DRAG,
    },
    events: {
      move: ctrl.userMove,
      drop: (piece, dest, prom) => ctrl.userDrop(piece as Piece, dest, prom),
      insert(elements) {
        if (elements) resizeHandle(elements, ctrl.pref.resizeHandle, { visible: () => true });
      },
    },
    premovable: {
      enabled: opts.premovable!.enabled,
    },
    predroppable: {
      enabled: opts.predroppable!.enabled,
    },
    drawable: {
      enabled: true,
    },
    highlight: {
      lastDests: ctrl.pref.highlightLastDests,
      check: ctrl.pref.highlightCheck,
    },
    animation: {
      enabled: true,
      duration: ctrl.pref.animation.duration,
    },
    hands: {
      inlined: true,
    },
    disableContextMenu: true,
  };
}
