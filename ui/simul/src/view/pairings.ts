import { destroy, initOneWithState } from 'common/mini-board';
import * as status from 'game/status';
import { i18n, i18nFormat, i18nVdom } from 'i18n';
import { h, type VNode } from 'snabbdom';
import type SimulCtrl from '../ctrl';
import type { Pairing } from '../interfaces';

export default function (ctrl: SimulCtrl): VNode {
  return h('div.game-list.now-playing.box__pad', ctrl.data.pairings.map(miniPairing(ctrl)));
}

const miniPairing = (ctrl: SimulCtrl) => (pairing: Pairing) => {
  const game = pairing.game;
  const player = pairing.player;
  const isOver = pairing.game.status >= status.ids.mate;
  const resultFirstLine = isOver
    ? i18nVdom('xPlayedY', ctrl.data.host.name, h('strong', player.name))
    : i18nVdom('xIsPlayingY', ctrl.data.host.name, h('strong', player.name));
  const resultWinner = isOver
    ? pairing.game.winner
      ? pairing.game.winner === pairing.hostColor
        ? i18nFormat('xWon', ctrl.data.host.name)
        : i18nFormat('xLost', ctrl.data.host.name)
      : i18n('draw')
    : undefined;
  const hostOutcomeKls = pairing.game.winner
    ? pairing.game.winner === pairing.hostColor
      ? '.win'
      : '.loss'
    : '';

  return h(
    'a',
    {
      class: {
        host: ctrl.data.host.gameId === game.id && ctrl.data.isRunning,
      },
      attrs: {
        href: `/${game.id}/${game.orient}`,
      },
    },
    [
      h(
        `span.mini-board.mini-board-${game.id}.parse-sfen.v-${game.variant}`,
        {
          props: {
            'data-color': game.orient,
            'data-sfen': game.sfen,
            'data-lastmove': game.lastMove,
            'data-variant': game.variant,
          },
          hook: {
            insert: vnode => {
              initOneWithState(vnode.elm as HTMLElement, {
                sfen: game.sfen,
                orientation: game.orient,
                lastMove: game.lastMove,
                variant: game.variant,
              });
            },
            destroy: vnode => destroy(vnode.elm as HTMLElement),
          },
        },
        h('div.sg-wrap'),
      ),
      h(`div.vstext${hostOutcomeKls}`, [
        h('div.top-row', resultFirstLine),
        h('div.winner-row', resultWinner),
      ]),
    ],
  );
};
