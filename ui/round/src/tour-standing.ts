import type { ChatPlugin } from 'chat/interfaces';
import { loadCssPath } from 'common/assets';
import { icons } from 'common/icons';
import type { Team, TourPlayer } from 'game/interfaces';
import { i18n } from 'i18n';
import { h, type VNode } from 'snabbdom';
import { onInsert } from './util';

export interface TourStandingCtrl extends ChatPlugin {
  set(players: TourPlayer[]): void;
}

export function tourStandingCtrl(players: TourPlayer[], team: Team | undefined): TourStandingCtrl {
  return {
    set(d: TourPlayer[]) {
      players = d;
    },
    tab: {
      key: 'tourStanding',
      name: i18n('standing'),
    },
    view(): VNode {
      return h(
        'div',
        {
          hook: onInsert(_ => {
            loadCssPath('round.tour-standing');
          }),
        },
        [
          team
            ? h(
                'h3.text',
                {
                  attrs: { 'data-icon': icons.banner },
                },
                team.name,
              )
            : null,
          h('table.slist', [
            h(
              'tbody',
              players.map((p: TourPlayer, i: number) => {
                return h(`tr.${p.n}`, [
                  h('td.name', [
                    h('span.rank', `${i + 1}`),
                    h(
                      'a.user-link.ulpt',
                      {
                        attrs: { href: `/@/${p.n}` },
                      },
                      (p.t ? `${p.t} ` : '') + p.n,
                    ),
                  ]),
                  h(
                    'td.total',
                    p.f
                      ? {
                          class: { 'is-gold': true },
                          attrs: { 'data-icon': icons.streak },
                        }
                      : {},
                    `${p.s}`,
                  ),
                ]);
              }),
            ),
          ]),
        ],
      );
    },
  };
}
