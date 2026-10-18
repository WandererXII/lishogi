import { icons } from 'common/icons';
import { h, type VNode } from 'snabbdom';
import type { Mode } from './dasher';

export type Close = () => void;
export type Open = (sub: Mode) => void;

export function bind(
  eventName: string,
  f: (e: Event) => void,
  redraw: Redraw | undefined = undefined,
) {
  return {
    insert: (vnode: VNode): void => {
      (vnode.elm as HTMLElement).addEventListener(eventName, e => {
        e.stopPropagation();
        f(e);
        if (redraw) redraw();
        return false;
      });
    },
  };
}

export function header(name: string, close: Close): VNode {
  return h(
    'a.head.text',
    {
      attrs: { 'data-icon': icons.left },
      hook: bind('click', close),
    },
    name,
  );
}

export function validateUrl(url: string | undefined): boolean {
  // modules/common/src/main/Form.scala
  return (
    !url ||
    url === '' ||
    ((url.startsWith('https://') || url.startsWith('//')) && url.length >= 10 && url.length <= 400)
  );
}

export function urlInput<K extends string>(
  key: K,
  value: string,
  set: (key: K, value: string) => void,
  title: string,
): VNode {
  return h('div.url-wrap', [
    h('p', title),
    h('input', {
      attrs: {
        type: 'text',
        placeholder: 'https://',
        value: value,
      },
      hook: {
        insert: vm =>
          $(vm.elm as HTMLInputElement).on('change keyup paste', function (this: HTMLInputElement) {
            const url = $(this).val()?.trim();
            if (validateUrl(url)) set(key, url || '');
          }),
      },
    }),
  ]);
}
