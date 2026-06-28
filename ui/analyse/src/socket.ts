import { ops as treeOps } from 'tree';
import type AnalyseCtrl from './ctrl';
import type { ServerEvalData } from './interfaces';

interface Handlers {
  [key: string]: any; // #TODO
}

interface Req {
  [key: string]: any; // #TODO
}

export interface Socket {
  send: Socket.Send;
  receive(type: string, data: any): boolean;
  sendAnaUsi(req: Req): void;
  sendForecasts(req: Req): void;
}

export function make(send: Socket.Send, ctrl: AnalyseCtrl): Socket {
  let anaUsiTimeout: number | undefined;

  // forecast mode: reload when opponent moves
  if (ctrl.data.forecast)
    setTimeout(() => {
      send('startWatching', ctrl.data.game.id);
    }, 1000);

  function currentChapterId(): string | undefined {
    if (ctrl.study) return ctrl.study.vm.chapterId;
    return undefined;
  }

  function addStudyData(req: any, isWrite = false): void {
    const c = currentChapterId();
    if (c) {
      req.ch = c;
      if (isWrite) {
        if (ctrl.study!.isWriting()) {
          if (!ctrl.study!.vm.mode.sticky) req.sticky = false;
        } else req.write = false;
      }
    }
  }

  const handlers: Handlers = {
    node(data: any) {
      clearTimeout(anaUsiTimeout);
      // no strict equality here!
      if (data.ch == currentChapterId()) ctrl.addNode(data.node, data.path);
      else console.log('socket handler node got wrong chapter id', data);
    },
    stepFailure() {
      clearTimeout(anaUsiTimeout);
      ctrl.reset();
    },
    sfen(e: any) {
      if (
        ctrl.forecast &&
        e.id === ctrl.data.game.id &&
        treeOps.last(ctrl.mainline)!.sfen.indexOf(e.sfen) !== 0
      ) {
        ctrl.forecast.reloadToLastPly();
      }
    },
    analysisProgress(data: ServerEvalData) {
      ctrl.mergeAnalysisData(data);
    },
    evalHit(e: Tree.ServerEval) {
      ctrl.evalCache.onCloudEval(e);
    },
    crowd(d: any) {
      ctrl.evalCache.upgradable(d.nb > 2);
      if (d.analysis) {
        ctrl.onlineUsers = d.analysis.users || [];
        ctrl.redraw();
      }
    },
  };

  function withoutStandardVariant(obj: any) {
    if (obj.variant === 'standard') delete obj.variant;
  }

  function sendAnaUsi(req: any) {
    clearTimeout(anaUsiTimeout);
    withoutStandardVariant(req);
    addStudyData(req, true);
    send('anaUsi', req);
    anaUsiTimeout = setTimeout(() => sendAnaUsi(req), 3000);
  }

  return {
    receive(type: string, data: any): boolean {
      const handler = handlers[type];
      if (handler) handler(data);
      return !!ctrl.study && ctrl.study.socketHandler(type, data);
    },
    sendAnaUsi,
    sendForecasts(req) {
      send('forecasts', req);
    },
    send,
  };
}
