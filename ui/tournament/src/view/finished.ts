import { loadLishogiScript } from 'common/assets';
import type { MaybeVNode, MaybeVNodes } from 'common/snabbdom';
import { once } from 'common/storage';
import { i18n, i18nFormatCapitalized } from 'i18n';
import { colorName } from 'shogi/color-name';
import { type VNode, h } from 'snabbdom';
import type TournamentController from '../ctrl';
import type { TournamentData } from '../interfaces';
import * as pagination from '../pagination';
import { podium, standing } from './arena';
import { teamStanding } from './battle';
import { arenaControls, organizedControls, robinControls } from './controls';
import header from './header';
import type { ViewHandler } from './main';
import { standing as oStanding } from './organized';
import playerInfo from './player-info';
import { podium as rPodium, standing as rStanding, recents } from './robin';
import teamInfo from './team-info';
import { numberRow } from './util';

function confetti(data: TournamentData): MaybeVNode {
  if (data.me && data.isRecentlyFinished && once(`tournament.end.canvas.${data.id}`))
    return h('canvas#confetti', {
      hook: {
        insert: vnode =>
          loadLishogiScript('misc.confetti').then(() => {
            window.lishogi.modules.miscConfetti(vnode.elm as HTMLCanvasElement);
          }),
      },
    });
  else return null;
}

function stats(data: TournamentData): VNode {
  const tableData = [
    numberRow(i18n('averageElo'), data.stats.averageRating, 'raw'),
    numberRow(i18n('gamesPlayed'), data.stats.games),
    numberRow(i18n('movesPlayed'), data.stats.moves),
    numberRow(
      i18nFormatCapitalized('xWins', colorName('sente', false)),
      [data.stats.senteWins, data.stats.games],
      'percent',
    ),
    numberRow(
      i18nFormatCapitalized('xWins', colorName('gote', false)),
      [data.stats.goteWins, data.stats.games],
      'percent',
    ),
    numberRow(i18n('draws'), [data.stats.draws, data.stats.games], 'percent'),
  ];

  if (data.berserkable) {
    const berserkRate = [data.stats.berserks / 2, data.stats.games];
    tableData.push(numberRow(i18n('berserkRate'), berserkRate, 'percent'));
  }

  return h('div.tour__stats', [h('h2', i18n('tournamentComplete')), h('table', tableData)]);
}

const name = 'finished';

function main(ctrl: TournamentController): MaybeVNodes {
  const pag = pagination.players(ctrl);
  const teamS = teamStanding(ctrl, 'finished');
  if (ctrl.isArena())
    return [
      ...(teamS
        ? [header(ctrl), teamS]
        : [h('div.big_top', [confetti(ctrl.data), header(ctrl), podium(ctrl)])]),
      arenaControls(ctrl, pag),
      standing(ctrl, pag),
    ];
  else if (ctrl.isRobin())
    return [
      ...(teamS
        ? [header(ctrl), teamS]
        : [h('div.big_top', [confetti(ctrl.data), header(ctrl), rPodium(ctrl)])]),
      robinControls(ctrl),
      rStanding(ctrl, 'finished'),
      recents(ctrl),
    ];
  else
    return [
      ...(teamS
        ? [header(ctrl), teamS]
        : [h('div.big_top', [confetti(ctrl.data), header(ctrl), rPodium(ctrl)])]),
      organizedControls(ctrl, pag),
      oStanding(ctrl, pag, 'finished'),
      recents(ctrl),
    ];
}

function table(ctrl: TournamentController): VNode | undefined {
  return ctrl.playerInfo.id
    ? playerInfo(ctrl)
    : ctrl.teamInfo.requested
      ? teamInfo(ctrl)
      : stats
        ? stats(ctrl.data)
        : undefined;
}

export const finished: ViewHandler = {
  name,
  main,
  table,
};
