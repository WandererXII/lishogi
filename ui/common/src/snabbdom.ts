import { type Attrs, type Hooks, h, type VNode } from 'snabbdom';
import { flagUrl } from './assets';
import { useJapanese } from './common';

export type MaybeVNode = VNode | string | null | undefined;
export type MaybeVNodes = MaybeVNode[];

export function onInsert<A extends HTMLElement>(f: (element: A) => void): Hooks {
  return {
    insert: vnode => f(vnode.elm as A),
  };
}

export function bind<K extends keyof HTMLElementEventMap>(
  eventName: K,
  f: (e: HTMLElementEventMap[K]) => any,
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

export const bindNonPassive = <K extends keyof HTMLElementEventMap>(
  eventName: K,
  f: (e: HTMLElementEventMap[K]) => any,
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
  return h(`blockquote.pull-quote${useJapanese() ? '.ja' : ''}`, [
    h('p', useJapanese() ? p.japanese : p.english),
  ]);
}

export function flagImage(countryCode: string): VNode {
  return h('img.flag', {
    attrs: {
      src: flagUrl(countryCode),
    },
  });
}
