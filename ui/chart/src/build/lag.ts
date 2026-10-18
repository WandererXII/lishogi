import type { ArcElement, ChartConfiguration, ChartDataset, ChartType } from 'chart.js';
import { cssVar } from 'common/styles';
import { wsAverageLag, wsOnOpen, wsSend } from 'common/ws';
import { borderColor, fontClearColor, fontFamily } from '../common';

declare module 'chart.js' {
  // @ts-ignore
  interface PluginOptionsByType<_TType extends ChartType> {
    needle?: {
      value: number;
    };
  }
}

window.Chart.defaults.font = fontFamily();

const v = {
  server: -1,
  network: -1,
};

function main(): void {
  wsOnOpen(() => wsSend('moveLat', true));

  $<HTMLCanvasElement>('.meter canvas').each(function (this: HTMLCanvasElement, index) {
    const colors = [cssVar('--c-good'), cssVar('--c-warn'), cssVar('--c-bad')];
    const colorsOver = [cssVar('--c-good-over'), cssVar('--c-warn-over'), cssVar('--c-bad-over')];
    const dataset: ChartDataset<'doughnut'>[] = [
      {
        data: [500, 150, 100],
        backgroundColor: colors,
        hoverBackgroundColor: colors,
        borderColor: borderColor(),
        borderWidth: 2,
        circumference: 180,
        rotation: 270,
      },
    ];
    const config: ChartConfiguration<'doughnut'> = {
      type: 'doughnut',
      data: {
        labels: ['0-500', '500-650', '650-750'],
        datasets: dataset,
      },
      options: {
        events: [],
        layout: {
          padding: {
            top: -50,
          },
        },
        plugins: {
          legend: {
            display: false,
          },
          title: {
            display: true,
            text: '',
            padding: { top: 100 },
            color: fontClearColor(),
          },
          needle: {
            value: index ? v.network : v.server,
          },
          datalabels: {
            color: (context: any) => {
              return colorsOver[context.dataIndex];
            },
            font: {
              size: 15,
            },
            formatter: (_: any, ctx: any) => ctx.chart.data.labels![ctx.dataIndex],
          },
        },
      },
      plugins: [
        {
          id: 'needle',
          afterDatasetDraw(chart, _args, _opts) {
            const ctx = chart.ctx;
            ctx.save();
            const data = chart.getDatasetMeta(0).data[0] as ArcElement;
            const first = chart.data.datasets[0].data[0] as number;
            let dest = data.circumference / Math.PI / first;
            dest = dest * (chart.options.plugins?.needle?.value ?? 1);
            const outer = data.outerRadius;
            ctx.translate(data.x, data.y);
            ctx.rotate(Math.PI * (dest + 1.5));
            ctx.beginPath();
            ctx.fillStyle = fontClearColor();
            ctx.moveTo(0 - 10, 0);
            ctx.lineWidth = 1;
            ctx.lineTo(0, -outer);
            ctx.lineTo(0 + 10, 0);
            ctx.lineTo(0 - 10, 0);
            ctx.fill();

            ctx.beginPath();
            ctx.arc(0, 0, 9, 0, (Math.PI / 180) * 360, false);
            ctx.fill();

            ctx.restore();
          },
        },
      ],
    };
    const chart = new window.Chart(this, config);
    if (index === 0)
      window.lishogi.pubsub.on('socket.in.mlat', (d: number) => {
        v.server = d;
        if (v.server < 0) return;
        chart.options.plugins!.needle!.value = Math.min(750, v.server);
        chart.options.plugins!.title!.text = makeTitle(index, v.server);
        updateAnswer();
      });
    else {
      setInterval(() => {
        v.network = Math.round(wsAverageLag());
        if (v.network < 0) return;
        chart.options.plugins!.needle!.value = Math.min(750, v.network);
        chart.options.plugins!.title!.text = makeTitle(index, v.network);
        updateAnswer();
      }, 1000);
    }
    const updateAnswer = () => {
      if (v.server === -1 || v.network === -1) return;
      const c =
        v.server <= 100 && v.network <= 500 ? 'nope-nope' : v.server <= 100 ? 'nope-yep' : 'yep';
      $('.lag .answer span').addClass('none').parent().find(`.${c}`).removeClass('none');
      chart.update();
    };
  });
}

const makeTitle = (index: number, lat: number) => [
  `${index ? 'Ping' : 'Server latency'} in milliseconds`,
  `${lat}`,
];

window.lishogi.ready.then(() => main());
