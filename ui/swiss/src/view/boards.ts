import { VNode, h } from 'snabbdom';
import { Board, BoardPlayer } from '../interfaces';
import { player as renderPlayer } from './util';

export function many(boards: Board[]): VNode {
  return h('div.swiss__boards.now-playing', boards.map(renderBoard));
}

export function top(boards: Board[]): VNode {
  return h('div.swiss__board__top.swiss__table', boards.slice(0, 1).map(renderBoard));
}

const renderBoard = (board: Board): VNode =>
  h('div.swiss__board', [boardPlayer(board.gote), miniBoard(board), boardPlayer(board.sente)]);

const boardPlayer = (player: BoardPlayer) =>
  h('div.swiss__board__player', [h('strong', '#' + player.rank), renderPlayer(player, true, true)]);

function miniBoard(board: Board) {
  return h(
    'a.mini-board.live.mini-board-' + board.id,
    {
      key: board.id,
      attrs: {
        href: '/' + board.id,
        'data-live': board.id,
        'data-color': 'sente',
        'data-sfen': board.sfen,
        'data-lastmove': board.lastMove,
      },
      hook: {
        insert(vnode) {
          window.lishogi.parseSfen($(vnode.elm as HTMLElement));
          window.lishogi.pubsub.emit('content_loaded');
        },
      },
    },
    [h('div.sg-wrap')]
  );
}
