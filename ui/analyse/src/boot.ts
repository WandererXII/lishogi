import { wsConnect } from 'common/ws';
import type AnalyseCtrl from './ctrl';
import type { AnalyseOpts } from './interfaces';

export function replay(opts: AnalyseOpts, start: (opts: AnalyseOpts) => AnalyseCtrl): AnalyseCtrl {
  let ctrl: AnalyseCtrl | undefined;

  const data = opts.data;

  opts.$meta = $('.analyse__side .game__meta').clone();
  opts.$streamers = $('.analyse__side .context-streamers').clone();
  opts.$underboard = $('.analyse__underboard').clone();
  opts.initialPly = 'url';

  opts.socketSend = wsConnect(
    `/watch/${data.game.id}/${data.player.color}/v6`,
    opts.socketVersion,
    {
      params: {
        userTv: data.userTv?.id,
      },
      receive: (t: string, d: any) => {
        ctrl?.socket.receive(t, d);
      },
      events: {},
    },
  ).send;

  ctrl = start(opts);

  return ctrl;
}

export function study(opts: AnalyseOpts, start: (opts: AnalyseOpts) => AnalyseCtrl): AnalyseCtrl {
  let ctrl: AnalyseCtrl | undefined;

  opts.$streamers = $('.context-streamers').clone();

  opts.initialPly = 'url';
  opts.socketSend = wsConnect(`/study/${opts.study.id}/socket/v5`, opts.socketVersion, {
    options: { reloadOnResume: true },
    receive: (t: string, d: any) => {
      ctrl?.socket.receive(t, d);
    },
  }).send;
  ctrl = start(opts);
  return ctrl;
}

export function analysis(
  opts: AnalyseOpts,
  start: (opts: AnalyseOpts) => AnalyseCtrl,
): AnalyseCtrl {
  let ctrl: AnalyseCtrl | undefined;

  opts.initialPly = 'url';
  opts.$side = $('.analyse__side').clone();
  opts.socketSend = wsConnect('/analysis/socket/v4', false, {
    receive: (t: string, d: any) => {
      ctrl?.socket.receive(t, d);
    },
  }).send;

  ctrl = start(opts);

  return ctrl;
}

export function practice(
  opts: AnalyseOpts,
  start: (opts: AnalyseOpts) => AnalyseCtrl,
): AnalyseCtrl {
  let ctrl: AnalyseCtrl | undefined;
  opts.socketSend = wsConnect('/analysis/socket/v4', false, {
    options: { reloadOnResume: true },
    receive: (t: string, d: any) => {
      ctrl?.socket.receive(t, d);
    },
  }).send;
  ctrl = start(opts);

  return ctrl;
}
