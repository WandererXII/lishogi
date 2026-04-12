import { randomNumber } from 'common/common';
import throttle from 'common/throttle';
import type TournamentController from './ctrl';
import type { Arrangement } from './interfaces';

function onFail() {
  setTimeout(window.lishogi.reload, randomNumber(9000));
}

function join(ctrl: TournamentController, password?: string, team?: string) {
  return window.lishogi.xhr
    .json('POST', `/tournament/${ctrl.data.id}/join`, {
      json: {
        p: password || null,
        team: team || null,
      },
    })
    .then(res => {
      if (!res.ok)
        res.json().then((t: any) => {
          if (t.error) alert(t.error);
          else window.lishogi.reload();
        });
    }, onFail);
}

function withdraw(ctrl: TournamentController): Promise<any> {
  return window.lishogi.xhr.json('POST', `/tournament/${ctrl.data.id}/withdraw`).catch(onFail);
}

function challenge(ctrl: TournamentController, a: Arrangement): Promise<any> {
  const arrId = ctrl.isRobin() ? a.id : `${ctrl.data.id}/${a.id}`;
  return window.lishogi.xhr
    .json('POST', `/challenge/tournament/${arrId}`)
    .then(data => {
      if (data.redirect) {
        ctrl.tourRedirect = true;
        window.lishogi.redirect(data.redirect);
      }
    })
    .catch(err => {
      try {
        const res = err as Response;
        res.json().then(t => {
          alert(t.error || t);
        });
      } catch {
        alert(`Cannot challenge player - ${err}`);
      }
    });
}

function loadPage(ctrl: TournamentController, p: number): Promise<void> {
  return window.lishogi.xhr
    .json('GET', `/tournament/${ctrl.data.id}/standing/${p}`)
    .then(data => {
      ctrl.loadPage(data);
      ctrl.redraw();
    })
    .catch(onFail);
}

function loadPageOf(ctrl: TournamentController, userId: string): Promise<any> {
  return window.lishogi.xhr.json('GET', `/tournament/${ctrl.data.id}/page-of/${userId}`);
}

function reload(ctrl: TournamentController, partial = true): Promise<void> {
  // try to avoid reload if we are redirecting
  if (window.lishogi.properReload || window.lishogi.redirectInProgress || ctrl.tourRedirect) {
    return Promise.resolve();
  } else
    return window.lishogi.xhr
      .json('GET', `/tournament/${ctrl.data.id}`, {
        url: {
          page: ctrl.isArena() ? ctrl.page : undefined,
          playerInfo: ctrl.playerInfo.id,
          partial,
        },
      })
      .then(data => {
        ctrl.reload(data);
        ctrl.redraw();
        const extraDelay = Math.floor(ctrl.nbWatchers) * (data.me ? 1 : 3);
        return new Promise(resolve => setTimeout(resolve, extraDelay));
      }, onFail);
}

const playerInfo = (ctrl: TournamentController, userId: string): Promise<void> =>
  window.lishogi.xhr.json('GET', `/tournament/${ctrl.data.id}/player/${userId}`).then(data => {
    ctrl.setPlayerInfoData(data);
    ctrl.redraw();
  }, onFail);

const teamInfo = (ctrl: TournamentController, teamId: string): Promise<void> =>
  window.lishogi.xhr.json('GET', `/tournament/${ctrl.data.id}/team/${teamId}`).then(data => {
    ctrl.setTeamInfo(data);
    ctrl.redraw();
  }, onFail);

interface Xhr {
  join: (ctrl: TournamentController, password?: string, team?: string) => void;
  withdraw: (ctrl: TournamentController) => void;
  challenge: (ctrl: TournamentController, a: Arrangement) => void;
  loadPage: (ctrl: TournamentController, p: number) => void;
  loadPageOf: typeof loadPageOf;
  reloadSoon: (...args: any[]) => void;
  reloadNow: (...args: any[]) => void;
  playerInfo: typeof playerInfo;
  teamInfo: typeof teamInfo;
}

const xhrFunctions: Xhr = {
  join: throttle(1000, join),
  withdraw: throttle(1000, withdraw),
  challenge: throttle(1000, challenge),
  loadPage: throttle(1000, loadPage),
  loadPageOf: loadPageOf,
  reloadSoon: throttle(4000, reload),
  reloadNow: throttle(500, reload),
  playerInfo: playerInfo,
  teamInfo: teamInfo,
};

export default xhrFunctions;
