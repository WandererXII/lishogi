import { wsConnect } from 'common/ws';
import type TournamentController from './ctrl';
import type { TournamentOpts } from './interfaces';

export function boot(
  opts: TournamentOpts,
  start: (opts: TournamentOpts) => TournamentController,
): TournamentController {
  $('body').data('tournament-id', opts.data.id);
  let ctrl: TournamentController | undefined;

  opts.socketSend = wsConnect(`/tournament/${opts.data.id}/socket/v4`, opts.data.socketVersion!, {
    options: { reloadOnResume: true },
    receive: (t: string, d: any) => ctrl?.socket.receive(t, d),
  }).send;
  ctrl = start(opts);

  if (opts.playerManagementButton) {
    opts.playerManagementButton.addEventListener('click', () => {
      ctrl.playerManagement = !ctrl.playerManagement;
      ctrl.redraw();
    });
  }

  return ctrl;
}
