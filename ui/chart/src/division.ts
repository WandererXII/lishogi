import type { ChartDataset, Point } from 'chart.js';
import type { Division } from 'game/interfaces';
import { i18n } from 'i18n';
import { chartYMax, chartYMin } from './common';

export default function (div?: Division): ChartDataset<'line'>[] {
  const lines: { div: string; loc: number }[] = [];
  if (div?.middle) {
    if (div.middle > 1) lines.push({ div: i18n('opening'), loc: 1 });
    lines.push({ div: i18n('middlegame'), loc: div.middle });
  }
  if (div?.end) {
    if (div.end > 1 && !div?.middle) lines.push({ div: i18n('middlegame'), loc: 0 });
    lines.push({ div: i18n('endgame'), loc: div.end });
  }
  const annotationColor = '#707070';

  /**  Instead of using the annotation plugin, create a dataset to plot as a pseudo-annotation
   *  @returns An array of vertical lines from {div,-1.05} to {div,+1.05}.
   * */
  return lines.map(line => ({
    type: 'line',
    xAxisID: 'x',
    yAxisID: 'y',
    label: line.div,
    data: [
      { x: line.loc, y: chartYMin },
      { x: line.loc, y: chartYMax },
    ],
    pointHoverRadius: 0,
    borderWidth: 1,
    borderColor: annotationColor,
    pointRadius: 0,
    order: 1,
    datalabels: {
      offset: -5,
      align: 45,
      rotation: 90,
      formatter: (val: Point) => (val.y > 0 ? line.div : ''),
    },
  }));
}
