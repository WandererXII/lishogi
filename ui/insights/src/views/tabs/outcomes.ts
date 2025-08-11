import { i18n, i18nPluralSame } from 'i18n';
import { h, type VNode } from 'snabbdom';
import type InsightCtrl from '../../ctrl';
import type { CounterObj, OutcomeResult, StatusId } from '../../types';
import { toPercentage } from '../../util';
import { barChart } from '../charts';
import { green, red, total } from '../colors';
import { horizontalBar, section, translateStatus } from '../util';

export function outcomes(ctrl: InsightCtrl, data: OutcomeResult): VNode {
  const key = JSON.stringify(ctrl.filter);
  return h('div.outcomes', [
    h('section.padding', winrateChart(data.winrate)),
    section(i18n('insights:termination'), barResultChart(data.winStatuses, data.lossStatuses, key)),
  ]);
}

function winrateChart(winrate: [number, number, number]): VNode {
  const totalGames = winrate.reduce((a, b) => a + b, 0);

  const winPercent = toPercentage(winrate[0], totalGames);
  const drawPercent = toPercentage(winrate[1], totalGames);
  const lossPercent = toPercentage(winrate[2], totalGames);
  return h('div.winrate-wrap', [
    h('div.winrate-info-wrap', [
      winrateInfo('win', winPercent, winrate[0]),
      drawPercent ? winrateInfo('draw', drawPercent, winrate[1]) : null,
      winrateInfo('loss', lossPercent, winrate[2]),
    ]),
    horizontalBar([winPercent, drawPercent, lossPercent], ['win', 'draw', 'loss']),
  ]);
}

function winrateInfo(type: 'win' | 'draw' | 'loss', percent: number, nbGames: number): VNode {
  return h(`div.winrate-info.winrate-info__${type}`, [
    h('span.wr_percent', `${percent}% `),
    h('span.wr_nb-games', i18nPluralSame('nbGames', nbGames)),
  ]);
}

function barResultChart(
  wins: CounterObj<StatusId>,
  losses: CounterObj<StatusId>,
  key: string,
): VNode {
  const winKeys = Object.keys(wins).map(n => Number.parseInt(n)) as StatusId[];
  const totalWins = winKeys.reduce((a, b) => a + (wins[b] || 0), 0);
  const lossKeys = Object.keys(losses).map(n => Number.parseInt(n)) as StatusId[];
  const totalLosses = lossKeys.reduce((a, b) => a + (losses[b] || 0), 0);

  const allKeys = [...new Set([...winKeys, ...lossKeys])];

  const valueMap = (n: number | string): string =>
    i18nPluralSame('nbGames', typeof n === 'number' ? n : Number.parseInt(n));

  return barChart('terminations', key, {
    labels: allKeys.map(key => translateStatus(key)),
    datasets: [
      {
        label: i18n('insights:wins'),
        backgroundColor: green,
        data: allKeys.map(key => wins[key] || 0),
        tooltip: {
          valueMap,
          total: totalWins,
        },
      },
      {
        label: i18n('insights:losses'),
        backgroundColor: red,
        data: allKeys.map(key => losses[key] || 0),
        tooltip: {
          valueMap,
          total: totalLosses,
        },
      },
      {
        label: i18n('insights:total'),
        borderColor: total,
        backgroundColor: `${total}33`,
        data: allKeys.map(key => (wins[key] || 0) + (losses[key] || 0)),
        hidden: true,
        tooltip: {
          valueMap,
        },
      },
    ],
    total: totalWins + totalLosses,
    opts: {},
  });
}
