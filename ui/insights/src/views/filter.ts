import { icons } from 'common/icons';
import { bind, dataIcon } from 'common/snabbdom';
import { i18n, i18nPluralSame } from 'i18n';
import { i18nVariant } from 'i18n/variant';
import { colorName } from 'shogi/color-name';
import { h, type VNode } from 'snabbdom';
import type InsightCtrl from '../ctrl';
import { allOptions } from '../filter';
import type { InsightFilter } from '../types';

export function filter(ctrl: InsightCtrl): VNode {
  return h('div.filter', [
    h(
      'h2.title-username',
      { class: { small: ctrl.username.length > 11 } },
      h('a.user-link', { attrs: { href: `/@/${ctrl.userId}` } }, ctrl.username),
    ),
    h(
      'div.filter-toggle',
      h(
        'div',
        {
          hook: bind(
            'click',
            () => {
              ctrl.filterToggle = !ctrl.filterToggle;
            },
            ctrl.redraw,
          ),
        },
        [
          i18n('filterGames'),
          h('i', { attrs: dataIcon(ctrl.filterToggle ? icons.up : icons.down) }),
        ],
      ),
    ),
    h(
      'div.filter-wrap',
      {
        class: {
          hide: !ctrl.filterToggle,
        },
      },
      [
        h('h2', i18n('filterGames')),
        options(ctrl, 'since', allOptions.since, nb => i18nPluralSame('nbDays', Number(nb))),
        options(ctrl, 'variant', allOptions.variant, i18nVariant),
        options(ctrl, 'color', allOptions.color, (s: 'both' | 'sente' | 'gote') =>
          s === 'both'
            ? `${colorName('sente', false)}/${colorName('gote', false)}`
            : colorName(s, false),
        ),
        options(ctrl, 'rated', allOptions.rated, (s: 'both' | 'yes' | 'no') =>
          s === 'both' ? `${i18n('yes')}/${i18n('no')}` : s === 'yes' ? i18n('yes') : i18n('no'),
        ),
        options(ctrl, 'computer', allOptions.computer, (s: 'both' | 'yes' | 'no') =>
          s === 'both' ? `${i18n('yes')}/${i18n('no')}` : s === 'yes' ? i18n('yes') : i18n('no'),
        ),
      ],
    ),
  ]);
}

function options<T extends string>(
  ctrl: InsightCtrl,
  key: keyof InsightFilter,
  values: T[],
  display: (value: T) => string,
): VNode {
  const current = ctrl.filter[key];
  function value2option(value: string, name: string): VNode {
    return h(
      'option',
      {
        attrs: {
          value: value,
          selected: current === value,
        },
      },
      name,
    );
  }
  return h(`div.options.key-${key}`, [
    h('h3', i18ns[key]),
    h(
      'select',
      {
        attrs: { id: key },
        on: {
          change(e) {
            const value = (e.target as HTMLSelectElement).value;
            ctrl.updateFilter({ [key]: value });
          },
        },
      },
      values.map(v => value2option(v, display(v))),
    ),
  ]);
}

const i18ns: Record<keyof InsightFilter, string> = {
  since: i18n('search:from'),
  variant: i18n('variant'),
  color: i18n('insights:color'),
  rated: i18n('rated'),
  computer: i18n('computer'),
  custom: i18n('custom'),
};
