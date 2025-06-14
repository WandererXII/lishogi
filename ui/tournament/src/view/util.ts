import { initOneWithState } from 'common/mini-board';
import { numberFormat } from 'common/number';
import { proverb } from 'common/snabbdom';
import { type MaybeVNode, type MaybeVNodes, dataIcon } from 'common/snabbdom';
import { i18n } from 'i18n';
import { type VNode, h } from 'snabbdom';
import type TournamentController from '../ctrl';
import type { Arrangement, BasePlayer, Featured } from '../interfaces';

export function miniBoard(game: Featured): VNode {
  return h(
    `a.mini-board.v-${game.variant}.mini-board-${game.id}`,
    {
      key: game.id,
      attrs: {
        href: `/${game.id}${game.color === 'sente' ? '' : '/gote'}`,
      },
      hook: {
        insert(vnode) {
          initOneWithState(vnode.elm as HTMLElement, {
            variant: game.variant,
            sfen: game.sfen,
            orientation: game.color,
            lastMove: game.lastMove,
          });
        },
      },
    },
    h('div.sg-wrap'),
  );
}

export function ratio2percent(r: number): string {
  return `${Math.round(100 * r)}%`;
}

export function playerName(p: { name: string; title?: string } | undefined): MaybeVNodes {
  return p?.title ? [h('span.title', p.title), ` ${p.name}`] : [p?.name || i18n('anonymousUser')];
}

export function player(
  p: BasePlayer,
  asLink: boolean,
  withRating: boolean,
  defender = false,
  leader = false,
): VNode {
  return h(
    `a.ulpt.user-link${((p.title || '') + p.name).length > 15 ? '.long' : ''}`,
    {
      attrs: asLink ? { href: `/@/${p.name}` } : { 'data-href': `/@/${p.name}` },
      hook: {
        destroy: vnode => $.powerTip.destroy(vnode.elm as HTMLElement),
      },
    },
    [
      h(
        `span.name${defender ? '.defender' : leader ? '.leader' : ''}`,
        defender ? { attrs: dataIcon('5') } : leader ? { attrs: dataIcon('8') } : {},
        playerName(p),
      ),
      withRating ? h('span.rating', ` ${p.rating}${p.provisional ? '?' : ''}`) : null,
    ],
  );
}

export function numberRow(name: string, value: any, typ?: string): VNode {
  return h('tr', [
    h('th', name),
    h(
      'td',
      typ === 'raw'
        ? value
        : typ === 'percent'
          ? value[1] > 0
            ? ratio2percent(value[0] / value[1])
            : 0
          : numberFormat(value),
    ),
  ]);
}

export function preloadUserTips(el: HTMLElement): void {
  window.lishogi.powertip.manualUserIn(el);
}

export function arrangementHasUser(a: Arrangement, userId: string): boolean {
  return a.user1.id === userId || a.user2.id === userId;
}

export const flatpickrConfig: Parameters<(typeof window)['flatpickr']>[1] = {
  minDate: 'today',
  maxDate: new Date(Date.now() + 1000 * 3600 * 24 * 31 * 3),
  dateFormat: 'U',
  altInput: true,
  altFormat: 'Z',
  enableTime: true,
  time_24hr: true,
  formatDate: (date, format) => {
    if (format === 'U') return Math.floor(date.getTime()).toString();
    return date.toLocaleString();
  },
  parseDate: (dateString, format) => {
    if (format === 'U') {
      return new Date(Number.parseInt(dateString));
    }
    return new Date(dateString);
  },
  disableMobile: true,
  locale: document.documentElement.lang as any,
};

export function proverbWrap(ctrl: TournamentController): MaybeVNode {
  return ctrl.data.proverb ? proverb(ctrl.data.proverb) : null;
}
