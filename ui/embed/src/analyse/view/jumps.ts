import { icons } from 'common/icons';
import { bindMobileMousedown } from 'common/mobile';
import { onInsert } from 'common/snabbdom';
import { h, type VNode } from 'snabbdom';
import type { AnalyseCtrl } from '../ctrl';

export function renderJumps(ctrl: AnalyseCtrl): VNode {
  const canJumpPrev = ctrl.path !== '';
  const canJumpNext = !!ctrl.node.children[0];
  return h(
    'div.analyse__controls',
    h(
      'div.jumps',
      {
        hook: onInsert(el => {
          bindMobileMousedown(
            el,
            e => {
              const action = dataAct(e);
              if (action === 'prev') ctrl.prev();
              else if (action === 'next') ctrl.next();
            },
            ctrl.redraw,
          );
        }),
      },
      [jumpButton(icons.prev, 'prev', canJumpPrev), jumpButton(icons.next, 'next', canJumpNext)],
    ),
  );
}

function jumpButton(icon: string, effect: 'prev' | 'next', enabled: boolean): VNode {
  return h('button.fbt', {
    class: { disabled: !enabled },
    attrs: { 'data-act': effect, 'data-icon': icon },
  });
}

function dataAct(e: Event): string | null {
  const target = e.target as HTMLElement;
  return (
    target.getAttribute('data-act') || (target.parentNode as HTMLElement).getAttribute('data-act')
  );
}
