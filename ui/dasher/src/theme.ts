import { icons } from 'common/icons';
import { i18n } from 'i18n';
import { h, type VNode } from 'snabbdom';
import { bind, header, type Open } from './util';

type ThemeKey = string;
type Theme = {
  key: ThemeKey;
  name: string;
};

export interface ThemeData {
  current: ThemeKey;
  list: Theme[];
  thickGrid: boolean;
}

export interface ThemeCtrl {
  data: ThemeData;
  set(t: ThemeKey): void;
  setThickGrid(isThick: boolean): void;
  open: Open;
}

export function ctrl(data: ThemeData, redraw: Redraw, open: Open): ThemeCtrl {
  return {
    data,
    set(key: ThemeKey) {
      data.current = key;
      applyTheme(key, data.list);
      window.lishogi.xhr
        .text('POST', '/pref/theme', { formData: { theme: key } })
        .catch(() => window.lishogi.announce({ msg: 'Failed to save theme preference' }));
      redraw();
    },
    setThickGrid(isThick: boolean) {
      data.thickGrid = isThick;
      applyThickGrid(isThick);
      window.lishogi.xhr
        .text('POST', '/pref/thickGrid', {
          formData: { thickGrid: isThick ? 1 : 0 },
        })
        .catch(() => window.lishogi.announce({ msg: 'Failed to save preference' }));
      redraw();
    },
    open,
  };
}

export function view(ctrl: ThemeCtrl): VNode {
  const lastIndex = ctrl.data.list.length - 1;
  const list: (Theme | 'thickGrid')[] = [
    ...ctrl.data.list.slice(0, lastIndex),
    'thickGrid',
    ctrl.data.list[lastIndex],
  ];
  return h('div.sub.theme', [
    header(i18n('boardTheme'), () => ctrl.open('links')),
    h(
      'div.list',
      list.map(i => themeView(ctrl, i)),
    ),
  ]);
}

function themeView(ctrl: ThemeCtrl, t: Theme | 'thickGrid') {
  if (t === 'thickGrid') return thickGrid(ctrl);
  else if (t.key === 'custom') return customThemeView(ctrl);
  else
    return h(
      'a',
      {
        hook: bind('click', () => ctrl.set(t.key)),
        attrs: { title: t.name },
        class: { active: ctrl.data.current === t.key },
      },
      h(`span.${t.key}`),
    );
}

function thickGrid(ctrl: ThemeCtrl): VNode {
  const title = i18n('gridThick');
  return h(
    `div.thick-switch${['blue', 'gray', 'doubutsu'].includes(ctrl.data.current) ? '.disabled' : ''}`,
    [
      h('label', { attrs: { for: 'thickGrid' } }, title),
      h('div.switch', [
        h('input#thickGrid.cmn-toggle', {
          attrs: {
            type: 'checkbox',
            title: title,
            checked: ctrl.data.thickGrid,
          },
          hook: bind('change', (e: Event) =>
            ctrl.setThickGrid((e.target as HTMLInputElement).checked),
          ),
        }),
        h('label', { attrs: { for: 'thickGrid' } }),
      ]),
    ],
  );
}

function customThemeView(ctrl: ThemeCtrl): VNode {
  return h(
    'a.custom',
    {
      hook: bind('click', () => {
        ctrl.set('custom');
        ctrl.open('customTheme');
      }),
      attrs: { 'data-icon': icons.right },
      class: { active: ctrl.data.current === 'custom' },
    },
    i18n('customTheme'),
  );
}

function applyThickGrid(isThick: boolean) {
  $('body').toggleClass('thick-grid', isThick);
}

function applyTheme(key: ThemeKey, list: Theme[]) {
  document.querySelectorAll('body').forEach(el => {
    el.classList.remove(...list.map(t => t.key));
    el.classList.add(key);
  });
}
