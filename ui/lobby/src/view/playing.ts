import { destroy, initOneWithState } from 'common/mini-board';
import { i18n } from 'i18n';
import { engineNameFromCode } from 'shogi/engine-name';
import { rankFromRating } from 'shogi/rank';
import { usernameVNodes } from 'shogi/username';
import { h, type VNode } from 'snabbdom';
import type LobbyController from '../ctrl';

function timer(secondsLeft: number) {
  const date = Date.now() + secondsLeft * 1000;
  return h(
    'time.timeago',
    {
      hook: {
        insert(vnode) {
          (vnode.elm as HTMLElement).setAttribute('datetime', `${date}`);
        },
      },
    },
    window.lishogi.timeago.format(date),
  );
}

export default function (ctrl: LobbyController): VNode {
  return h(
    'div.now-playing',
    ctrl.data.nowPlaying.map(pov =>
      h(
        `a${pov.isMyTurn ? '.my_turn' : ''}`,
        {
          key: pov.gameId,
          attrs: { href: `/${pov.fullId}` },
        },
        [
          h(
            `div.mini-board.v-${pov.variant.key}`,
            {
              hook: {
                insert(vnode) {
                  initOneWithState(vnode.elm as HTMLElement, {
                    variant: pov.variant.key,
                    orientation: pov.color,
                    sfen: pov.sfen,
                    lastMove: pov.lastMove,
                  });
                },
                destroy: vnode => destroy(vnode.elm as HTMLElement),
              },
            },
            h('div.sg-wrap'),
          ),
          h('span.meta', [
            h(
              'span.user-link',
              usernameVNodes({
                username: pov.opponent.aiCode
                  ? engineNameFromCode(pov.opponent.aiCode)
                  : pov.opponent.username,
                rank:
                  pov.opponent.rating && !pov.opponent.prov
                    ? rankFromRating(pov.opponent.rating)
                    : undefined,
                title: pov.opponent.title,
                engineLvl: pov.opponent.ai,
                countryCode: pov.opponent.countryCode,
              }),
            ),
            h(
              'span.indicator',
              pov.isMyTurn
                ? pov.secondsLeft && pov.hasMoved
                  ? timer(pov.secondsLeft)
                  : [i18n('yourTurn')]
                : h('span', '\xa0'),
            ),
          ]),
        ],
      ),
    ),
  );
}
