import * as winningChances from 'ceval/winning-chances';
import type { ChartConfiguration, ChartDataset, PointStyle } from 'chart.js';
import { defined } from 'common/common';
import { cssVar } from 'common/styles';
import { plyOffset } from 'game/game';
import { mated } from 'game/status';
import { i18n } from 'i18n';
import {
  accentColor,
  axisOpts,
  fontFamily,
  goteColor,
  maybeChart,
  plyLine,
  selectPly,
  senteColor,
  tooltipConfig,
} from '../common';
import type { AcplChart, AnalyseData, Player } from '../interface';

const blursEnabled = true;

function main(
  el: HTMLCanvasElement,
  data: AnalyseData,
  mainline: Tree.Node[],
  ply: number | undefined,
): AcplChart {
  const possibleChart = maybeChart(el);
  if (possibleChart) return possibleChart as AcplChart;
  const blurBackgroundColorWhite = 'white';
  const blurBackgroundColorBlack = 'black';
  const firstPly = mainline[0].ply;
  const plyLineChart = plyLine(ply || firstPly);
  const isPartial = (d: AnalyseData) => !d.analysis || d.analysis.partial;

  const makeDataset = (
    d: AnalyseData,
    mainline: Tree.Node[],
  ): { acpl: ChartDataset<'line'>; moveLabels: string[]; adviceHoverColors: string[] } => {
    const pointBackgroundColors: string[] = [];
    const adviceHoverColors: string[] = [];
    const moveLabels: string[] = [];
    const pointStyles: PointStyle[] = [];
    const pointSizes: number[] = [];
    const winChances: { x: number; y: number }[] = [];
    const blurs = [toBlurArray(d.player), toBlurArray(d.opponent)];
    const pointRadius = mainline.length > 50 ? 4 : 5;
    if (d.player.color === 'sente') blurs.reverse();

    mainline.slice(1).forEach((node, index) => {
      const isSente = (node.ply & 1) === 1;
      let cp: number | undefined = node.eval && 0;
      if (node.eval?.mate)
        cp = node.eval.mate > 0 ? Number.POSITIVE_INFINITY : Number.NEGATIVE_INFINITY;
      else if (index === mainline.length - 2 && mated(d as any))
        cp = isSente ? Number.POSITIVE_INFINITY : Number.NEGATIVE_INFINITY;
      else if (defined(node.eval?.cp)) cp = node.eval.cp;
      const offset = plyOffset(data as any);

      const winchance = winningChances.povChances('sente', { cp: cp });
      // Plot winchance because logarithmic but display the corresponding cp.eval from AnalyseData in the tooltip
      winChances.push({ x: node.ply, y: winchance });

      const { advice, color: glyphColor } = glyphProperties(node);
      const label = `${node.ply - offset}.  ${node.notation}`;
      let annotation = '';
      if (advice) annotation = ` [${i18n(advice)}]`;
      const isBlur =
        blursEnabled &&
        blurs[isSente ? 1 : 0][Math.floor((node.ply - (d.game.startedAtStep || 0) - 1) / 2)] ===
          '1';
      if (isBlur) annotation = ' [blur]';
      moveLabels.push(label + annotation);
      pointStyles.push(isBlur ? 'rect' : 'circle');
      pointSizes.push(isBlur ? pointRadius : 0);
      pointBackgroundColors.push(
        isBlur ? (isSente ? blurBackgroundColorBlack : blurBackgroundColorWhite) : accentColor(),
      );
      adviceHoverColors.push(glyphColor ?? accentColor());
    });

    return {
      acpl: {
        label: i18n('advantage'),
        data: winChances,
        borderWidth: 2,
        fill: {
          target: 'origin',
          above: senteColor(),
          below: goteColor(),
        },
        pointRadius: d.player.blurs || d.opponent.blurs ? pointSizes : 0,
        pointHoverRadius: pointRadius,
        pointHoverBorderWidth: 0,
        pointHitRadius: 100,
        borderColor: accentColor(),
        pointBackgroundColor: pointBackgroundColors,
        pointStyle: pointStyles,
        hoverBackgroundColor: accentColor(),
        order: 5,
        datalabels: { display: false },
      },
      moveLabels: moveLabels,
      adviceHoverColors: adviceHoverColors,
    };
  };

  const dataset = makeDataset(data, mainline);
  const acpl = dataset.acpl;
  const moveLabels = dataset.moveLabels;
  let adviceHoverColors = dataset.adviceHoverColors;
  const config: ChartConfiguration<'line'> = {
    type: 'line',
    data: {
      labels: moveLabels.map((_, index) => index),
      datasets: [acpl, plyLineChart],
    },
    options: {
      clip: false,
      interaction: {
        mode: 'nearest',
        axis: 'x',
        intersect: false,
      },
      elements: {
        line: {
          tension: 0.1,
        },
      },
      scales: axisOpts(firstPly + 1, mainline.length + firstPly),
      animation: false,
      maintainAspectRatio: false,
      responsive: true,
      plugins: {
        tooltip: {
          ...tooltipConfig,
          titleFont: fontFamily(14, 'bold'),
          bodyFont: fontFamily(13),
          filter: (item: any) => item.datasetIndex === 0,
          callbacks: {
            label: (item: any) => {
              const ev = mainline[item.dataIndex + 1]?.eval;
              if (!ev) return ''; // Pos is mate
              let e = 0;
              let mateSymbol = '';
              let advantageSign = '';
              if (ev.cp) {
                e = Math.max(Math.min(Math.round(ev.cp / 10) / 10, 99), -99);
                if (ev.cp > 0) advantageSign = '+';
              }
              if (ev.mate) {
                e = ev.mate;
                mateSymbol = '#';
              }
              return `${i18n('advantage')}: ${mateSymbol}${advantageSign}${e}`;
            },
            title: (items: any) => (items[0] ? moveLabels[items[0].dataIndex] : ''),
          },
        },
        legend: {
          display: false,
        },
      },
      onClick(_event, elements, _chart) {
        const data = elements[elements.findIndex(element => element.datasetIndex === 0)];
        if (data) window.lishogi.pubsub.emit('analysis.chart.click', data.index);
      },
    },
  };
  const acplChart = new window.Chart(el, config) as AcplChart;
  acplChart.selectPly = selectPly.bind(acplChart);
  acplChart.updateData = (d: AnalyseData, mainline: Tree.Node[]) => {
    const dataset = makeDataset(d, mainline);
    adviceHoverColors = dataset.adviceHoverColors;
    const acpl = dataset.acpl;
    acplChart.data.datasets[0].data = acpl.data;
    if (!isPartial(data)) christmasTree(acplChart, mainline, adviceHoverColors);
    acplChart.update('none');
  };
  window.lishogi.pubsub.on('ply', acplChart.selectPly);
  if (!isPartial(data)) christmasTree(acplChart, mainline, adviceHoverColors);
  return acplChart;
}

type Advice = 'blunder' | 'mistake' | 'inaccuracy';
const glyphProperties = (node: Tree.Node): { advice?: Advice; color?: string } => {
  if (node.glyphs?.some(g => g.id === 4))
    return { advice: 'blunder', color: cssVar('--c-blunder-clear') };
  else if (node.glyphs?.some(g => g.id === 2))
    return { advice: 'mistake', color: cssVar('--c-mistake-clear') };
  else if (node.glyphs?.some(g => g.id === 6))
    return { advice: 'inaccuracy', color: cssVar('--c-inaccuracy-clear') };
  else return { advice: undefined, color: undefined };
};

const toBlurArray = (player: Player) => player.blurs?.bits?.split('') ?? [];

function christmasTree(chart: AcplChart, mainline: Tree.Node[], hoverColors: string[]) {
  $('div.advice-summary').on('mouseenter', 'tr.symbol', function (this: HTMLElement) {
    const symbol = this.getAttribute('data-symbol');
    const playerColorBit = this.getAttribute('data-color') === 'sente' ? 1 : 0;
    const acplDataset = chart.data.datasets[0];
    if (symbol === '??' || symbol === '?!' || symbol === '?') {
      acplDataset.pointHoverBackgroundColor = hoverColors;
      const points = mainline
        .filter(
          node =>
            node.glyphs?.some(glyph => glyph.symbol === symbol) &&
            (node.ply & 1) === playerColorBit,
        )
        .map(node => ({ datasetIndex: 0, index: node.ply - mainline[0].ply - 1 }));
      chart.setActiveElements(points);
      chart.update('none');
    }
  });
  $('div.advice-summary').on('mouseleave', 'tr.symbol', function (this: HTMLElement) {
    chart.setActiveElements([]);
    chart.data.datasets[0].pointHoverBackgroundColor = accentColor();
    chart.update('none');
  });
}

window.lishogi.registerModule(__bundlename__, main);
