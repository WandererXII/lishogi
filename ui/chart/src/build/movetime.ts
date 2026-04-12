import type { Chart, ChartDataset, PointStyle } from 'chart.js';
import { cssVar } from 'common/styles';
import { plyOffset } from 'game/game';
import { i18nPluralSame } from 'i18n';
import {
  axisOpts,
  goteColor,
  type MovePoint,
  maybeChart,
  plyLine,
  primaryColor,
  selectPly,
  senteColor,
  tooltipConfig,
} from '../common';
import division from '../division';
import type { AnalyseData, Player, PlyChart } from '../interface';

function movetime(
  el: HTMLCanvasElement,
  data: AnalyseData,
  ply: number | undefined,
): PlyChart | undefined {
  const possibleChart = maybeChart(el);
  if (possibleChart) return possibleChart as PlyChart;
  const moveCentis = data.game.moveCentis;
  const byoEntry = data.game.byoEntry;

  // Nothing for imported games or games with too little moves
  if (!moveCentis || moveCentis.length <= 3) return;
  type PlotSeries = { sente: MovePoint[]; gote: MovePoint[] };
  const moveSeriesPlot: PlotSeries = {
    sente: [],
    gote: [],
  };
  const totalSeriesPlot: PlotSeries = {
    sente: [],
    gote: [],
  };
  const labels: string[] = [];
  const colors = ['sente', 'gote'] as const;
  const pointStyles: { sente: PointStyle[]; gote: PointStyle[] } = { sente: [], gote: [] };
  const pointRadius: { sente: number[]; gote: number[] } = { sente: [], gote: [] };

  const tree = data.treeParts;
  const firstPly = tree[0].ply;
  for (let i = 0; i <= firstPly; i++) {
    labels.push('');
  }
  const offset = plyOffset(data as any);

  const logC = Math.log(3) ** 2;

  const blurs = [toBlurArray(data.player), toBlurArray(data.opponent)];
  if (data.player.color === 'sente') blurs.reverse();

  moveCentis.forEach((centis: number, x: number) => {
    const node = tree[x + 1];
    if (!tree[x]) return;
    const ply = node ? node.ply : tree[x].ply + 1;
    const notation = node ? node.notation : '-';
    // Current behaviour: Game-ending action is assigned to the next color
    // regardless of whether they made it or not
    // e.g. Sente makes a move and then immediately resigns

    const color = ply & 1;
    const colorName = color ? 'sente' : 'gote';

    const y = Math.max(Math.log(0.005 * Math.min(centis, 12e4) + 3) ** 2 - logC, x > 1 ? 0.3 : 0);
    let label = `${ply - (offset % 2)}. ${notation}`;
    const movePoint: MovePoint = {
      x: node ? node.ply : tree[x].ply + 1,
      y: color ? y : -y,
    };

    if (blurs[color].shift() === '1') {
      pointStyles[colorName].push('rect');
      pointRadius[colorName].push(4.5);
      label += ' [blur]';
    } else {
      pointStyles[colorName].push('circle');
      pointRadius[colorName].push(0);
    }

    const seconds = (centis / 100).toFixed(centis >= 200 ? 1 : 2);
    label += `\n${i18nPluralSame('nbSeconds', Number(seconds))}`;
    moveSeriesPlot[colorName].push(movePoint);

    let clock = node ? node.clock : undefined;
    if (clock === undefined) {
      if (data.game.status.name === 'outoftime') clock = 0;
      else if (data.clock) {
        const prevClock = tree[x - 1].clock;
        if (prevClock) clock = prevClock + data.clock.increment - centis;
      }
    }
    if (clock) {
      label += `\n${formatClock(clock)}`;
      totalSeriesPlot[colorName].push({
        x: node ? node.ply : tree[x].ply + 1,
        y: color ? Math.max(clock, 0) : Math.min(-clock, 0),
      });
    }
    labels.push(label);
  });

  const colorSeriesMax = (series: PlotSeries) =>
    Math.max(
      ...colors.reduce((acc, color) => {
        acc.push(...series[color].map(point => Math.abs(point.y)));
        return acc;
      }, [] as number[]),
    );
  const totalSeriesMax = colorSeriesMax(totalSeriesPlot);
  const moveSeriesMax = colorSeriesMax(moveSeriesPlot);

  const lineBuilder = (series: PlotSeries, lineOnly: boolean): ChartDataset[] =>
    colors.map(color => ({
      type: 'line',
      data: series[color].map(point => ({
        x: point.x,
        y: point.y / totalSeriesMax,
      })),
      borderColor: primaryColor(),
      borderWidth: 2,
      pointHoverBackgroundColor: color === 'sente' ? senteColor() : goteColor(),
      pointHitRadius: lineOnly ? 200 : 0,
      pointHoverBorderColor: lineOnly ? primaryColor() : 'transparent',
      pointRadius: 0,
      pointHoverRadius: 5,
      pointStyle: undefined,
      fill: lineOnly
        ? {}
        : {
            target: 'origin',
            above: senteColor(),
            below: goteColor(),
          },
      order: lineOnly ? 1 : 3,
      datalabels: { display: false },
    }));

  const moveSeriesSet: ChartDataset[] = colors.map(color => ({
    type: 'bar',
    data: moveSeriesPlot[color].map(point => ({ x: point.x, y: point.y / moveSeriesMax })),
    maxBarThickness: 75,
    backgroundColor: color === 'sente' ? cssVar('--c-sente2') : cssVar('--c-gote2'),
    hitRadius: 200,
    grouped: false,
    categoryPercentage: 2,
    barPercentage: 1,
    order: 2,
    borderSkipped: false,
    borderWidth: 1,
    datalabels: { display: false },
  }));
  const divisionLines = division(data.game.division);
  const datasets: ChartDataset<any>[] = [];
  if (labels.length > 8) {
    datasets.push(...moveSeriesSet);
  }

  if (!data.clock || data.clock.initial !== 0) {
    datasets.push(...lineBuilder(totalSeriesPlot, false));
    datasets.push(...lineBuilder(totalSeriesPlot, true));
  }
  datasets.push(plyLine(ply || firstPly), ...divisionLines);

  if (byoEntry?.sente)
    datasets.push(plyLine(byoEntry.sente * 2 - 1, false, { borderColor: senteColor() }));
  if (byoEntry?.gote)
    datasets.push(plyLine(byoEntry.gote * 2 - 1, false, { borderColor: goteColor() }));

  const config: Chart['config'] = {
    type: 'line' /* Needed for compat. with plyline and divisionlines.
     * Makes the x-axis 'linear' instead of 'category'.
     * Side effect: makes the chart smaller than the canvas area.
     */,
    data: {
      labels: labels,
      datasets: datasets,
    },
    options: {
      maintainAspectRatio: false,
      responsive: true,
      animation: false,
      scales: axisOpts(
        firstPly + 1,
        // Omit game-ending action to sync acpl and movetime charts
        labels.length - (labels[labels.length - 1].includes('-') ? 1 : 0),
      ),
      layout: {
        padding: 10,
      },
      clip: false,
      plugins: {
        legend: {
          display: false,
        },
        tooltip: {
          ...tooltipConfig,
          callbacks: {
            title: (items: any) =>
              labels[items[0].dataset.label === 'bar' ? items[0].parsed.x * 2 : items[0].parsed.x],
            label: () => '',
          },
        },
      },
      onClick(_event, elements, _chart) {
        let blackOffset = elements[0].datasetIndex & 1;
        if ((firstPly & 1) !== 0) blackOffset = blackOffset ^ 1;
        window.lishogi.pubsub.emit('analysis.chart.click', elements[0].index * 2 + blackOffset);
      },
    },
  };
  const movetimeChart = new window.Chart(el, config) as PlyChart;
  movetimeChart.selectPly = selectPly.bind(movetimeChart);
  window.lishogi.pubsub.on('ply', movetimeChart.selectPly);
  return movetimeChart;
}

const toBlurArray = (player: Player) => (player.blurs?.bits ? player.blurs.bits.split('') : []);

const formatClock = (centis: number) => {
  let result = '';
  if (centis >= 60 * 60 * 100) result += `${Math.floor(centis / 60 / 6000)}:`;
  result += `${Math.floor((centis % (60 * 6000)) / 6000)
    .toString()
    .padStart(2, '0')}:`;
  const secs = (centis % 6000) / 100;
  if (centis < 6000) result += secs.toFixed(2).padStart(5, '0');
  else result += Math.floor(secs).toString().padStart(2, '0');
  return result;
};

window.lishogi.registerModule(__bundlename__, movetime);
