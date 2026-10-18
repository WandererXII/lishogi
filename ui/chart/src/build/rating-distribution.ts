import { TinyColor } from '@ctrl/tinycolor';
import type { ChartConfiguration, ChartData, ChartDataset } from 'chart.js';
import { i18n } from 'i18n';
import {
  borderColor,
  bragColor,
  fontClearColor,
  primaryColor,
  secondaryColor,
  shadeColor,
  tooltipConfig,
} from '../common';
import type { DistributionData } from '../interface';

function main(data: DistributionData): void {
  $<HTMLCanvasElement>('#rating_distribution_chart').each(function (this: HTMLCanvasElement) {
    const ratingAt = (i: number) => 400 + i * 25;
    const arraySum = (arr: number[]) => arr.reduce((a, b) => a + b, 0);
    const sum = arraySum(data.freq);
    const cumul: number[] = [];
    const ratings: number[] = [];
    for (let i = 0; i < data.freq.length; i++) {
      ratings.push(ratingAt(i));
      cumul.push(arraySum(data.freq.slice(0, i)) / sum);
    }
    const mainColor = new TinyColor(primaryColor()).lighten(20).toString();
    const gradient = this.getContext('2d')?.createLinearGradient(0, 0, 0, 400);
    gradient?.addColorStop(0, mainColor);
    gradient?.addColorStop(1, 'transparent');
    const seriesCommonData = (color: string): Partial<ChartDataset<'line'>> => ({
      pointHoverRadius: 6,
      pointHoverBorderWidth: 2,
      pointHoverBorderColor: fontClearColor(),
      borderColor: color,
      pointBackgroundColor: color,
    });
    const maxRating = Math.max(...ratings);

    const datasets: ChartDataset<'line'>[] = [
      {
        ...seriesCommonData(secondaryColor()),
        data: cumul,
        yAxisID: 'y2',
        label: i18n('cumulative'),
        pointRadius: 0,
        datalabels: { display: false },
        pointHitRadius: 200,
      },
    ];
    const pushLine = (color: string, rating: number, label: string) =>
      datasets.push({
        ...seriesCommonData(color),
        yAxisID: 'y2',
        data: [
          { x: rating, y: 0 },
          { x: rating, y: Math.max(...cumul) },
        ],
        segment: {
          borderDash: [10],
        },
        label: label,
        pointRadius: 4,
        datalabels: {
          align: 'top',
          offset: 0,
          display: 'auto',
          formatter: value => (value.y === 0 ? '' : label),
          color: color,
        },
      });
    if (data.myRating && data.myRating <= maxRating)
      pushLine(bragColor(), data.myRating, `${i18n('yourRating')} (${data.myRating})`);
    if (data.otherRating && data.otherPlayer) {
      pushLine(
        shadeColor(),
        Math.min(data.otherRating, maxRating),
        `${data.otherPlayer} (${data.otherRating})`,
      );
    }
    datasets.push({
      ...seriesCommonData(mainColor),
      data: data.freq,
      backgroundColor: gradient,
      yAxisID: 'y',
      fill: true,
      label: i18n('players'),
      pointRadius: 4,
      datalabels: { display: false },
      pointHitRadius: 200,
    });
    const chartData: ChartData<'line'> = {
      labels: ratings,
      datasets: datasets,
    };

    const config: ChartConfiguration<'line'> = {
      type: 'line',
      data: chartData,
      options: {
        clip: false,
        scales: {
          x: {
            type: 'linear',
            min: Math.min(...ratings),
            max: maxRating,
            grid: {
              color: borderColor(),
            },
            ticks: {
              stepSize: 100,
              format: {
                useGrouping: false,
              },
            },
            title: {
              display: true,
              text: i18n('glicko2Rating'),
            },
          },
          y: {
            grid: {
              color: borderColor(),
              tickLength: 0,
            },
            ticks: {
              padding: 10,
            },
            title: {
              display: true,
              text: i18n('players'),
            },
          },
          y2: {
            position: 'right',
            grid: {
              display: false,
            },
            ticks: {
              format: {
                style: 'percent',
                maximumFractionDigits: 1,
              },
            },
            title: {
              display: true,
              text: i18n('cumulative'),
            },
          },
        },
        animation: false,
        locale: document.documentElement.lang,
        maintainAspectRatio: false,
        responsive: true,
        plugins: {
          legend: {
            display: false,
          },
          tooltip: {
            ...tooltipConfig,
          },
        },
      },
    };
    new window.Chart(this, config);
  });
}

window.lishogi.registerModule(__bundlename__, main);
