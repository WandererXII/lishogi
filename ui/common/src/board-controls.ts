import { h, type VNode } from 'snabbdom';
import { icons } from './icons';
import { bindMobileMousedown } from './mobile';
import { bind, type MaybeVNode, onInsert } from './snabbdom';

interface JumpButton {
  click: () => void;
  disabled: boolean;
  glowing?: boolean;
}

interface JumpsConfig {
  first: JumpButton;
  prev: JumpButton;
  next: JumpButton;
  last: JumpButton;
  redraw: Redraw;
}

type JumpDirection = 'first' | 'prev' | 'next' | 'last';

function dataAct(e: Event): string | null {
  const target = e.target as HTMLElement;
  return (
    target.getAttribute('data-act') || (target.parentNode as HTMLElement).getAttribute('data-act')
  );
}

function repeater(jump: JumpButton, redraw: Redraw) {
  const repeat = () => {
    jump.click();
    redraw();
    delay = Math.max(100, delay - delay / 15);
    timeout = setTimeout(repeat, delay);
  };
  let delay = 350;
  let timeout = setTimeout(repeat, 500);
  jump.click();
  document.addEventListener('pointerup', () => clearTimeout(timeout), {
    once: true,
  });
}

function jumpButton(direction: JumpDirection, jump: JumpButton): VNode {
  return h('button.fbt', {
    class: { disabled: !!jump.disabled, glowing: !!jump.glowing },
    attrs: {
      'data-act': direction,
      'data-icon': icons[direction],
    },
  });
}

export function jumpsControl(jumps: JumpsConfig): VNode {
  return h(
    'div.jumps',
    {
      hook: onInsert(el => {
        bindMobileMousedown(
          el,
          e => {
            const action = dataAct(e);
            if (action === 'prev') repeater(jumps.prev, jumps.redraw);
            if (action === 'next') repeater(jumps.next, jumps.redraw);
            else if (action === 'first') jumps.first.click();
            else if (action === 'last') jumps.last.click();
          },
          jumps.redraw,
        );
      }),
    },
    [
      jumpButton('first', jumps.first),
      jumpButton('prev', jumps.prev),
      jumpButton('next', jumps.next),
      jumpButton('last', jumps.last),
    ],
  );
}

interface Button {
  act: string;
  title?: string;
  icon?: string;
  text?: string;
  href?: string;
  cls?: Record<string, boolean>;
}

function createButton(b: Button | undefined): MaybeVNode {
  if (!b) return;

  return h(
    `${b.href ? 'a' : 'div'}.fbt`,
    {
      key: JSON.stringify(b.cls || { act: b.act }),
      class: b.cls || {},
      attrs: {
        title: b.title || false,
        href: b.href || false,
        'data-act': b.act || false,
        'data-icon': b.icon || false,
      },
    },
    b.text,
  );
}

export function boardControls(config: {
  col1: (Button | undefined)[];
  col2: {
    left?: Button;
    right?: Button;
  };
  onClick: (act: string) => void;
  jumps: JumpsConfig;
}): VNode {
  return h(
    'div.board-controls',
    {
      hook: bind('click', e => {
        const action = dataAct(e);
        if (action) config.onClick(action);
      }),
    },
    [
      h('div.col1-controls', [
        h(
          'div.col1-controls-scrollable',
          h('div.col1-controls-scrollable-inner', config.col1.map(createButton)),
        ),
        jumpsControl(config.jumps),
      ]),
      h('div.col2-controls', [
        createButton(config.col2.left),
        jumpsControl(config.jumps),
        createButton(config.col2.right),
      ]),
    ],
  );
}
