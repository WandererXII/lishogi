import { idleTimer } from 'common/timings';
import type LobbyController from './ctrl';
import * as hookRepo from './hook-repo';
import type { Hook } from './interfaces';

interface Handlers {
  [key: string]: (data: any) => void;
}

export default class LobbySocket {
  handlers: Handlers;

  constructor(
    readonly send: Socket.Send,
    ctrl: LobbyController,
  ) {
    this.send = send;

    this.handlers = {
      had(hook: Hook) {
        const flushable = hookRepo.add(ctrl, hook);
        if (flushable) ctrl.flushHooks(true);
        ctrl.redraw();
      },
      hrm(ids: string) {
        ids.match(/.{8}/g)!.forEach(id => {
          hookRepo.remove(ctrl, id);
        });
        ctrl.redraw();
      },
      hooks(hooks: Hook[]) {
        hookRepo.setAll(ctrl, hooks);
        ctrl.flushHooks(true);
        ctrl.redraw();
      },
      hli(ids: string) {
        hookRepo.syncIds(ctrl, ids.match(/.{8}/g) || []);
        ctrl.redraw();
      },
      reload_seeks() {
        if (ctrl.tab === 'seeks') ctrl.seeksNow();
        else if (ctrl.tab === 'presets') ctrl.seeksEventually();
        else ctrl.reloadSeeks = true;
      },
    };

    idleTimer(
      3 * 60 * 1000,
      () => send('idle', true),
      () => {
        send('idle', false);
        ctrl.awake();
      },
    );
  }

  realTimeIn(): void {
    this.send('hookIn');
  }
  realTimeOut(): void {
    this.send('hookOut');
  }

  receive = (type: string, data: any): boolean => {
    if (this.handlers[type]) {
      this.handlers[type](data);
      return true;
    }
    return false;
  };
}
