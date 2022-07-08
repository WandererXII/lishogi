import { h, VNode } from 'snabbdom';
import { Shogiground } from 'shogiground';
import { Config as SgConfig } from 'shogiground/config';
import * as cg from 'shogiground/types';
import resizeHandle from 'common/resize';
import CoordinateTrainerCtrl from './ctrl';

export default function (ctrl: CoordinateTrainerCtrl): VNode {
  return h('div.cg-wrap', {
    hook: {
      insert: vnode => {
        const el = vnode.elm as HTMLElement;
        ctrl.shogiground = Shogiground(el, makeConfig(ctrl));
      },
      destroy: () => ctrl.shogiground!.destroy(),
    },
  });
}

function makeConfig(ctrl: CoordinateTrainerCtrl): SgConfig {
  return {
    orientation: ctrl.orientation,
    blockTouchScroll: true,
    coordinates: false,
    addPieceZIndex: ctrl.config.is3d,
    movable: { free: false, color: undefined },
    drawable: { enabled: false },
    draggable: { enabled: false },
    selectable: { enabled: false },
    events: {
      insert(elements: cg.Elements) {
        resizeHandle(elements, ctrl.config.resizePref, ctrl.playing ? 2 : 0);
      },
      select: ctrl.onShogigroundSelect,
    },
  };
}
