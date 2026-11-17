import { icons } from 'common/icons';
import { destroy, initOneWithState } from 'common/mini-board';
import { numberFormat } from 'common/number';
import { bind, dataIcon, type MaybeVNode, type MaybeVNodes, proverb } from 'common/snabbdom';
import { i18n } from 'i18n';
import { rankFromRating } from 'shogi/rank';
import { usernameVNodes } from 'shogi/username';
import { h, type VNode } from 'snabbdom';
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
        destroy: vnode => destroy(vnode.elm as HTMLElement),
      },
    },
    h('div.sg-wrap'),
  );
}

export function ratio2percent(r: number): string {
  return `${Math.round(100 * r)}%`;
}

export function playerName(
  p:
    | { name: string; title?: string; rating?: number; provisional?: boolean; countryCode?: string }
    | undefined,
): MaybeVNodes {
  return usernameVNodes({
    username: p?.name || '?',
    title: p?.title,
    rank: p?.rating && !p.provisional ? rankFromRating(p.rating) : undefined,
    countryCode: p?.countryCode,
  });
}

export function player(
  p: BasePlayer,
  config: {
    asLink?: boolean;
    withRating?: boolean;
    defender?: boolean;
    leader?: boolean;
    status?: { online: boolean };
  },
): VNode {
  return h(
    `a.ulpt.user-link${p.name.length > 15 ? '.long' : ''}${config.status?.online ? '.online' : ''}`,
    {
      attrs: config.asLink
        ? {
            'data-user-title': p.title || '',
            href: `/@/${p.name}`,
          }
        : { 'data-href': `/@/${p.name}`, 'data-user-title': p.title || '' },
      hook: {
        destroy: vnode => $.powerTip.destroy(vnode.elm as HTMLElement),
      },
    },
    [
      config.status ? h(`i.line${p.patron ? '.patron' : ''}`) : null,
      h(
        `span.name${config.defender ? '.defender.text' : config.leader ? '.leader.text' : ''}`,
        config.leader ? { attrs: dataIcon(icons.crown) } : {},
        playerName(p),
      ),
      config.withRating ? h('span.rating', ` ${p.rating}${p.provisional ? '?' : ''}`) : null,
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
  return a.user1?.id === userId || a.user2?.id === userId;
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

export function backControl(f: () => void): VNode {
  return h('div.tour__controls.back', [
    h(
      'div.pager',
      { hook: bind('click', () => f()) },
      h(
        'button.fbt.is.text.' + 'back',
        {
          attrs: {
            'data-icon': icons.left,
            title: i18n('back'),
          },
        },
        i18n('back'),
      ),
    ),
  ]);
}
