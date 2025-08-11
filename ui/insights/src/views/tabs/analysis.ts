import { i18n } from 'i18n';
import { allRoles } from 'shogiops/variant/util';
import { h, type VNode } from 'snabbdom';
import type InsightCtrl from '../../ctrl';
import type { AnalysisResult, InsightFilter } from '../../types';
import { fixed } from '../../util';
import { barChart, lineChart } from '../charts';
import { accent, accuracy, primary, total } from '../colors';
import { bigNumberWithDesc, section, translateRole } from '../util';

export function analysis(ctrl: InsightCtrl, data: AnalysisResult): VNode {
  return h('div.analysis', [
    h(
      'section',
      h('div.one-third-wrap', [
        bigNumberWithDesc(fixed(data.accuracy), i18n('insights:averageAccuracy'), 'total', '%'),
        accuracyByResult(data),
      ]),
    ),
    section(i18n('insights:accuracyByMoveNumber'), accuracyByMoveNumber(data, ctrl.filter)),
    section(i18n('insights:accuracyByPiece'), accuracyByRoleChart(data, ctrl.filter)),
  ]);
}

function accuracyByResult(data: AnalysisResult): VNode {
  const outcomes = [i18n('wins'), i18n('draws'), i18n('losses')];
  return h(
    'div.accuracy-by-result',
    data.accuracyByOutcome.map((nb, i) => {
      if (nb === 0 && i === 1) return null;
      return h('div.accuracy-by-result__item', [
        bigNumberWithDesc(fixed(nb), outcomes[i], 'accuracy', '%'),
        verticalBar(['win', 'draw', 'loss'][i], [100, nb], ['not-accuracy', 'accuracy']),
      ]);
    }),
  );
}

function verticalBar(name: string, numbers: number[], cls: string[] = []): VNode {
  return h(
    `div.simple-vertical-bar.${name}`,
    numbers
      .filter(n => n > 5)
      .map((n, i) => h(`div${cls[i] ? `.${cls[i]}` : ''}`, { style: { height: `${n}%` } })),
  );
}

function accuracyByMoveNumber(data: AnalysisResult, flt: InsightFilter): VNode {
  const d = data.accuracyByMoveNumber;
  const maxKey = Object.keys(d).reduce((a, b) => Math.max(a, Number.parseInt(b) || 0), 0);
  const labels = [...Array(maxKey).keys()];
  return lineChart('line-by-move-number-chart', JSON.stringify(flt), {
    labels: labels.map(n => n.toString()),
    datasets: [
      {
        label: i18n('insights:accuracy'),
        borderColor: accuracy,
        backgroundColor: `${accuracy}55`,
        data: labels.map(key => fixed(d[key])),
        tooltip: {
          valueMap: (value: number) => `${i18n('insights:average')}: ${value}`,
          counts: labels.map(key => data.accuracyByMoveNumberCount[key] || 0),
        },
      },
    ],
    opts: { percentage: true, autoSkip: true },
  });
}

function accuracyByRoleChart(data: AnalysisResult, flt: InsightFilter): VNode {
  const variant = flt.variant;
  const moves = data.accuracyByMoveRole;
  const drops = data.accuracyByDropRole;
  const movesAndDrops = data.accuracyByRole;
  const roles = allRoles(variant);
  const valueMap = (value: number | string) => `${i18n('insights:average')}: ${value}`;

  return barChart('moves-drops-by-role', JSON.stringify(flt), {
    labels: roles.map(r => translateRole(r).split(' ')),
    datasets: [
      {
        label: i18n('insights:moves'),
        backgroundColor: primary,
        data: roles.map(key => fixed(moves[key]), 3),
        tooltip: {
          valueMap,
          counts: roles.map(key => data.accuracyByMoveRoleCount[key] || 0),
        },
      },
      {
        label: i18n('insights:drops'),
        backgroundColor: accent,
        data: roles.map(key => fixed(drops[key], 3)),
        tooltip: {
          valueMap,
          counts: roles.map(key => data.accuracyByDropRoleCount[key] || 0),
        },
      },
      {
        label: i18n('insights:total'),
        backgroundColor: total,
        data: roles.map(key => fixed(movesAndDrops[key], 3)),
        hidden: true,
        tooltip: {
          valueMap,
          counts: roles.map(key => data.accuracyByRoleCount[key] || 0),
        },
      },
    ],
    opts: { percentage: true },
  });
}
