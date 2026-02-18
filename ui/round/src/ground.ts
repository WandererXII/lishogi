import { prefs } from 'common/prefs';
import { notationFiles, notationRanks } from 'shogi/notation';
import { predrop } from 'shogi/pre-drop';
import { premove } from 'shogi/pre-move';
import resizeHandle from 'shogi/resize';
import type { Config } from 'shogiground/config';
import { usiToSquareNames } from 'shogiops/compat';
import { forsythToRole, parseSfen, roleToForsyth } from 'shogiops/sfen';
import type { Piece, Role } from 'shogiops/types';
import { makeSquareName, parseSquareName } from 'shogiops/util';
import {
  handRoles,
  pieceCanPromote,
  pieceForcePromote,
  promotableOnDrop,
  promote,
} from 'shogiops/variant/util';
import { h, type VNode } from 'snabbdom';
import type RoundController from './ctrl';
import type { RoundData } from './interfaces';
import { firstPly, plyStep } from './round';
import * as util from './util';

const noCheckHighlightVariants: VariantKey[] = ['chushogi', 'dobutsu'];

export function makeConfig(ctrl: RoundController): Config {
  const data = ctrl.data;
  const variant = data.game.variant.key;
  const hooks = ctrl.makeSgHooks();
  const step = plyStep(data, ctrl.ply);
  const playing = ctrl.isPlaying();
  const posRes = playing ? parseSfen(variant, step.sfen, false) : undefined;
  const splitSfen = step.sfen.split(' ');
  const sealedUsi = data.player.sealedUsi && usiToSquareNames(data.player.sealedUsi);
  const isProMode = !!data.game.isProMode;
  const enablePre = data.pref.enablePremove && !isProMode;

  return {
    sfen: { board: splitSfen[0], hands: splitSfen[2] },
    orientation: boardOrientation(data, ctrl.flip),
    turnColor: step.ply % 2 === 0 ? 'sente' : 'gote',
    activeColor: playing ? data.player.color : undefined,
    lastDests: step.usi ? usiToSquareNames(step.usi) : undefined,
    checks: step.check,
    coordinates: {
      enabled: data.pref.coords !== prefs.coords.NONE,
      files: notationFiles(),
      ranks: notationRanks(),
    },
    highlight: {
      lastDests: data.pref.highlightLastDests,
      check:
        data.pref.highlightCheck &&
        !noCheckHighlightVariants.includes(variant) &&
        !data.game.isProMode,
    },
    events: {
      move: hooks.onMove,
      drop: hooks.onDrop,
      select: (key: Key) => {
        if (
          ctrl.lionFirstMove &&
          ctrl.shogiground.state.movable.dests &&
          !ctrl.shogiground.state.movable.dests.has(key)
        ) {
          ctrl.cancelLionMidMove();
        }
      },
      unselect: (key: Key) => {
        if (ctrl.lionFirstMove && ctrl.lionFirstMove.to === parseSquareName(key)) {
          const from = ctrl.lionFirstMove.from;
          const to = ctrl.lionFirstMove.to;
          hooks.onUserMove(makeSquareName(from), makeSquareName(to), false, { premade: false });
        }
      },
      insert(elements) {
        if (elements)
          resizeHandle(elements, data.pref.resizeHandle, {
            ply: ctrl.ply,
            initialPly: firstPly(ctrl.data),
          });
      },
    },
    hands: {
      roles: handRoles(variant),
      inlined: variant !== 'chushogi',
    },
    movable: {
      free: false,
      dests: playing && posRes ? util.getMoveDests(posRes, !!data.game.isProMode) : new Map(),
      showDests: data.pref.destination,
      events: {
        after: hooks.onUserMove,
      },
    },
    droppable: {
      free: false,
      dests: playing && posRes ? util.getDropDests(posRes, !!data.game.isProMode) : new Map(),
      showDests: data.pref.destination && data.pref.dropDestination,
      events: {
        after: hooks.onUserDrop,
      },
    },
    promotion: {
      promotesTo: (role: Role) => {
        return promote(variant)(role);
      },
      movePromotionDialog: (orig: Key, dest: Key) => {
        const piece = ctrl.shogiground.state.pieces.get(orig) as Piece | undefined;
        const capture = ctrl.shogiground.state.pieces.get(dest) as Piece | undefined;
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
        const piece = ctrl.shogiground.state.pieces.get(orig) as Piece | undefined;
        return !!piece && pieceForcePromote(variant)(piece, parseSquareName(dest)!);
      },
    },
    forsyth: {
      fromForsyth: forsythToRole(variant),
      toForsyth: roleToForsyth(variant),
    },
    animation: {
      enabled: true,
      duration: data.pref.animationDuration,
    },
    premovable: {
      enabled: enablePre,
      generate: enablePre ? premove(variant) : undefined,
      showDests: data.pref.destination,
    },
    predroppable: {
      enabled: enablePre,
      generate: enablePre ? predrop(variant) : undefined,
      showDests: data.pref.destination && data.pref.dropDestination,
    },
    draggable: {
      enabled: data.pref.moveEvent !== prefs.moveEvent.CLICK,
      showGhost: data.pref.highlightLastDests,
      showTouchSquareOverlay: data.pref.squareOverlay,
    },
    selectable: {
      enabled: data.pref.moveEvent !== prefs.moveEvent.DRAG,
    },
    drawable: {
      enabled: true,
      squares: sealedUsi ? sealedUsi.map(s => ({ key: s, className: 'sealed' })) : [],
    },
  };
}

export function reload(ctrl: RoundController): void {
  ctrl.shogiground.set(makeConfig(ctrl));
}

export function boardOrientation(data: RoundData, flip: boolean): Color {
  return flip ? data.opponent.color : data.player.color;
}

export function renderBoard(ctrl: RoundController): VNode {
  return h('div.sg-wrap', {
    hook: {
      insert: vnode => {
        ctrl.shogiground.attach({ board: vnode.elm as HTMLElement });
      },
    },
  });
}
