import { i18n } from 'i18n';
import { allRoles } from 'shogiops/variant/util';
import { h, type VNode } from 'snabbdom';
import type InsightCtrl from '../../ctrl';
import type { InsightFilter, TimesResult } from '../../types';
import { fixed } from '../../util';
import { barChart } from '../charts';
import { accent, primary, total } from '../colors';
import { section, translateRole } from '../util';

export function times(ctrl: InsightCtrl, data: TimesResult): VNode {
  return h('div.times', [
    h('section.padding', [
      h('div.third-wrap', [
        h('div.big-number-with-desc.total', [
          h('div.big-number', secondsToString(data.totalTime)),
          h('span.desc', i18n('insights:totalTimeSpentThinking')),
        ]),
        h('div.big-number-with-desc.game', [
          h('div.big-number', secondsToString(data.avgTimePerGame)),
          h('span.desc', i18n('insights:averageTimePerGame')),
        ]),
        h('div.big-number-with-desc.move-drop', [
          h('div.big-number', secondsToString(data.avgTimePerMoveAndDrop)),
          h('span.desc', i18n('insights:averageTimePerMoveOrDrop')),
        ]),
      ]),
    ]),
    section(i18n('insights:timeSpentThinkingByPiece'), timesByRoleChart(data, ctrl.filter)),
  ]);
}

function secondsToString(seconds: number): VNode {
  const useMinutes = seconds > 3600; // 1 hour
  const useHours = seconds > 43200; // 12 hours
  if (useHours) return h('div', [fixed(seconds / 3600, 1), h('span.tiny', 'h')]);
  else if (useMinutes) return h('div', [fixed(seconds / 60, 0), h('span.tiny', 'm')]);
  else return h('div', [Math.round(seconds), h('span.tiny', 's')]);
}

function timesByRoleChart(data: TimesResult, flt: InsightFilter): VNode {
  const variant = flt.variant;
  const moves = data.sumOfTimesByMoveRole;
  const movesCnt = data.nbOfMovesByRole;
  const drops = data.sumOfTimesByDropRole;
  const dropsCnt = data.nbOfDropsByRole;
  const roles = allRoles(variant);
  const totals = roles.map(key => (moves[key] || 0) + (drops[key] || 0));
  const totalsCnt = roles.map(key => (movesCnt[key] || 0) + (dropsCnt[key] || 0));
  const totalMoves = roles.reduce((a, b) => a + (moves[b] || 0), 0);
  const totalDrops = roles.reduce((a, b) => a + (drops[b] || 0), 0);

  const valueMap = (value: number | string): string => `Σ: ${value}`;

  return barChart('times-role-chart', JSON.stringify(flt), {
    labels: roles.map(r => translateRole(r).split(' ')),
    datasets: [
      {
        label: i18n('insights:moves'),
        backgroundColor: primary,
        data: roles.map(key => Math.round(moves[key] || 0)),
        tooltip: {
          valueMap,
          counts: roles.map(key => movesCnt[key] || 0),
          average: roles.map(key =>
            data.nbOfMovesByRole[key] ? (moves[key] || 0) / data.nbOfMovesByRole[key]! : 0,
          ),
          total: totalMoves,
        },
      },
      {
        label: i18n('insights:drops'),
        backgroundColor: accent,
        data: roles.map(key => Math.round(drops[key] || 0)),
        tooltip: {
          valueMap,
          counts: roles.map(key => dropsCnt[key] || 0),
          average: roles.map(key =>
            data.nbOfDropsByRole[key] ? (drops[key] || 0) / data.nbOfDropsByRole[key]! : 0,
          ),
          total: totalDrops,
        },
      },
      {
        label: i18n('insights:total'),
        backgroundColor: total,
        data: totals.map(n => Math.round(n)),
        hidden: true,
        tooltip: {
          valueMap,
          counts: totalsCnt,
          average: roles.map((_, i) => (totalsCnt[i] ? (totals[i] || 0) / totalsCnt[i]! : 0)),
        },
      },
    ],
    total: totalMoves + totalDrops,
    opts: { valueAffix: 's' },
  });
}
