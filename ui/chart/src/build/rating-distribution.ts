import type { ChartConfiguration, ChartData, ChartDataset } from 'chart.js';
import { i18n } from 'i18n';
import { fontFamily, gridColor, hoverBorderColor } from '../common';
import type { DistributionData } from '../interface';

function main(data: DistributionData): void {
  $('#rating_distribution_chart').each(function (this: HTMLCanvasElement) {
    const ratingAt = (i: number) => 400 + i * 25;
    const arraySum = (arr: number[]) => arr.reduce((a, b) => a + b, 0);
    const sum = arraySum(data.freq);
    const cumul: number[] = [];
    const ratings: number[] = [];
    for (let i = 0; i < data.freq.length; i++) {
      ratings.push(ratingAt(i));
      cumul.push(arraySum(data.freq.slice(0, i)) / sum);
    }
    const gradient = this.getContext('2d')?.createLinearGradient(0, 0, 0, 400);
    gradient?.addColorStop(0, 'rgba(119, 152, 191, 1)');
    gradient?.addColorStop(1, 'rgba(119, 152, 191, 0.3)');
    const seriesCommonData = (color: string): Partial<ChartDataset<'line'>> => ({
      pointHoverRadius: 6,
      pointHoverBorderWidth: 2,
      pointHoverBorderColor: hoverBorderColor,
      borderColor: color,
      pointBackgroundColor: color,
    });
    const maxRating = Math.max(...ratings);

    const datasets: ChartDataset<'line'>[] = [
      {
        ...seriesCommonData('#dddf0d'),
        data: cumul,
        yAxisID: 'y2',
        label: i18n('cumulative'),
        pointRadius: 0,
        datalabels: { display: false },
        pointHitRadius: 200,
      },
      {
        ...seriesCommonData('#7798bf'),
        data: data.freq,
        backgroundColor: gradient,
        yAxisID: 'y',
        fill: true,
        label: i18n('players'),
        pointRadius: 4,
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
      pushLine('#55bf3b', data.myRating, `${i18n('yourRating')} (${data.myRating})`);
    if (data.otherRating && data.otherPlayer) {
      pushLine(
        '#eeaaee',
        Math.min(data.otherRating, maxRating),
        `${data.otherPlayer} (${data.otherRating})`,
      );
    }
    const chartData: ChartData<'line'> = {
      labels: ratings,
      datasets: datasets,
    };

    const config: ChartConfiguration<'line'> = {
      type: 'line',
      data: chartData,
      options: {
        scales: {
          x: {
            type: 'linear',
            min: Math.min(...ratings),
            max: maxRating,
            grid: {
              color: gridColor,
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
              color: gridColor,
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
            titleFont: fontFamily(),
            bodyFont: fontFamily(),
            caretPadding: 8,
            callbacks: {
              label: (item: any) => (item.datasetIndex > 1 ? item.dataset.label : undefined),
            },
          },
        },
      },
    };
    new window.Chart(this, config);
  });
}

window.lishogi.registerModule(__bundlename__, main);
