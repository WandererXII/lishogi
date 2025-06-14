import { type Attrs, type Hooks, type VNode, h } from 'snabbdom';
import { useJp } from './common';

export type MaybeVNode = VNode | string | null | undefined;
export type MaybeVNodes = MaybeVNode[];

export function onInsert<A extends HTMLElement>(f: (element: A) => void): Hooks {
  return {
    insert: vnode => f(vnode.elm as A),
  };
}

export function bind(
  eventName: string,
  f: (e: Event) => any,
  redraw?: () => void,
  passive = true,
): Hooks {
  return onInsert(el =>
    el.addEventListener(
      eventName,
      e => {
        const res = f(e);
        if (res === false && !passive) e.preventDefault();
        redraw?.();
        return res;
      },
      { passive },
    ),
  );
}

export const bindNonPassive = (
  eventName: string,
  f: (e: Event) => any,
  redraw?: () => void,
): Hooks => bind(eventName, f, redraw, false);

export function bindSubmit(f: (e: Event) => unknown, redraw?: () => void): Hooks {
  return bind(
    'submit',
    e => {
      e.preventDefault();
      return f(e);
    },
    redraw,
    false,
  );
}

export function dataIcon(icon: string): Attrs {
  return {
    'data-icon': icon,
  };
}

export function proverb(p: Proverb): VNode {
  return h(`blockquote.pull-quote${useJp() ? '.jp' : ''}`, [
    h('p', useJp() ? p.japanese : p.english),
  ]);
}
