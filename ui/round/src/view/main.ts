import { hasTouchEvents } from 'common/mobile';
import stepwiseScroll from 'common/wheel';
import { render as keyboardMove } from 'keyboard-move';
import { h, type VNode } from 'snabbdom';
import type RoundController from '../ctrl';
import * as shogiground from '../ground';
import * as keyboard from '../keyboard';
import * as util from '../util';
import { impasseModal } from './impasse-modal';
import { renderTable } from './table';

export function main(ctrl: RoundController): VNode {
  const d = ctrl.data;

  return ctrl.nvui
    ? ctrl.nvui.render(ctrl)
    : h(
        `div.${ctrl.opts.klasses.join('.')}`,
        {
          class: { 'move-confirm': !!ctrl.usiToSubmit, 'pro-mode': !!d.game.isProMode },
        },
        [
          h(
            'div.round__app__board',
            h(
              `div.round__app__board-inner.main-board.v-${d.game.variant.key}`,
              {
                hook:
                  hasTouchEvents || window.lishogi.storage.get('scrollMoves') == '0'
                    ? undefined
                    : util.bind(
                        'wheel',
                        stepwiseScroll((e: WheelEvent, scroll: boolean) => {
                          if (!ctrl.isPlaying()) {
                            const target = e.target as HTMLElement;
                            if (target.tagName !== 'SG-PIECES') return;
                            e.preventDefault();
                            if (e.deltaY > 0 && scroll) keyboard.next(ctrl);
                            else if (e.deltaY < 0 && scroll) keyboard.prev(ctrl);
                            ctrl.redraw();
                          }
                        }),
                        undefined,
                        false,
                      ),
              },
              shogiground.renderBoard(ctrl),
            ),
          ),
          ...renderTable(ctrl),
          ctrl.keyboardMove ? keyboardMove(ctrl.keyboardMove) : null,
          ctrl.impasseHelp ? impasseModal(ctrl) : null,
        ],
      );
}
