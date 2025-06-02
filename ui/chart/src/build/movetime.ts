import type { Chart, ChartDataset, PointStyle } from 'chart.js';
import { i18nPluralSame } from 'i18n';
import {
  type MovePoint,
  axisOpts,
  fontColor,
  fontFamily,
  lineColor,
  maybeChart,
  plyLine,
  selectPly,
  tooltipBgColor,
} from '../common';
import division from '../division';
import type { AnalyseData, Player, PlyChart } from '../interface';

function movetime(el: HTMLCanvasElement, data: AnalyseData): PlyChart | undefined {
  const possibleChart = maybeChart(el);
  if (possibleChart) return possibleChart as PlyChart;
  const moveCentis = data.game.moveCentis;
  if (!moveCentis) return; // imported games
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
    let label = `${ply}. ${notation}`;
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

  const lineBuilder = (series: PlotSeries): ChartDataset[] =>
    colors.map(color => ({
      type: 'line',
      data: series[color].map(point => ({
        x: point.x,
        y: point.y / totalSeriesMax,
      })),
      borderColor: lineColor,
      borderWidth: 1.5,
      pointHitRadius: 200,
      pointHoverBorderColor: lineColor,
      pointRadius: 0,
      pointHoverRadius: 5,
      pointStyle: undefined,
      fill: {
        target: 'origin',
        above: 'rgba(0,0,0,0.1)',
        below: 'rgba(153, 153, 153, .1)',
      },
      order: 1,
      datalabels: { display: false },
    }));

  const moveSeriesSet: ChartDataset[] = colors.map(color => ({
    type: 'bar',
    data: moveSeriesPlot[color].map(point => ({ x: point.x, y: point.y / moveSeriesMax })),
    backgroundColor: color === 'sente' ? 'black' : 'white',
    grouped: false,
    categoryPercentage: 2,
    barPercentage: 1,
    order: 2,
    borderColor: color === 'sente' ? '#616161' : '#838383',
    borderWidth: 1,
    datalabels: { display: false },
  }));
  const divisionLines = division(data.game.division);
  const datasets: ChartDataset[] = [...moveSeriesSet];
  datasets.push(...lineBuilder(totalSeriesPlot));
  datasets.push(plyLine(firstPly), ...divisionLines);

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
      plugins: {
        legend: {
          display: false,
        },
        tooltip: {
          borderColor: fontColor,
          borderWidth: 1,
          backgroundColor: tooltipBgColor,
          caretPadding: 15,
          titleColor: fontColor,
          titleFont: fontFamily(13),
          displayColors: false,
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
  window.lishogi.pubsub.emit('ply.trigger');
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
