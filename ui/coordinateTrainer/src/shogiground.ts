import { h, VNode } from 'snabbdom';
import { Shogiground } from 'shogiground';
import { Config as SgConfig } from 'shogiground/config';
import * as sg from 'shogiground/types';
import resizeHandle from 'common/resize';
import CoordinateTrainerCtrl from './ctrl';

export default function (ctrl: CoordinateTrainerCtrl): VNode {
  return h('div.cg-wrap', {
    hook: {
      insert: () => (ctrl.shogiground = Shogiground(makeConfig(ctrl))),
      destroy: () => ctrl.shogiground!.destroy(),
    },
  });
}

function makeConfig(ctrl: CoordinateTrainerCtrl): SgConfig {
  return {
    orientation: ctrl.orientation,
    blockTouchScroll: true,
    coordinates: { enabled: false },
    movable: { free: false },
    drawable: { enabled: false },
    draggable: { enabled: false },
    selectable: { enabled: false },
    events: {
      insert(boardEls?: sg.BoardElements, _handEls?: sg.HandElements) {
        if (boardEls) resizeHandle(boardEls, ctrl.config.resizePref, ctrl.playing ? 2 : 0);
      },
      select: ctrl.onShogigroundSelect,
    },
    disableContextMenu: true,
  };
}
