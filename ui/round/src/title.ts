import { isPlayerTurn } from 'game/game';
import { aborted, finished, paused } from 'game/status';
import { i18n } from 'i18n';
import type RoundController from './ctrl';

const initialTitle = document.title;

let curFaviconIdx = 0;
const F = ['/assets/logo/lishogi-favicon-32.png', '/assets/logo/lishogi-favicon-32-invert.png'].map(
  (path, i) => () => {
    if (curFaviconIdx !== i) {
      (document.getElementById('favicon') as HTMLAnchorElement).href = path;
      curFaviconIdx = i;
    }
  },
);

let tickerTimer: number | undefined;
function resetTicker() {
  if (tickerTimer) clearTimeout(tickerTimer);
  tickerTimer = undefined;
  F[0]();
}

function startTicker() {
  function tick() {
    if (!document.hasFocus()) {
      F[1 - curFaviconIdx]();
      tickerTimer = setTimeout(tick, 1500);
    }
  }
  if (!tickerTimer) tickerTimer = setTimeout(tick, 200);
}

export function init(): void {
  window.addEventListener('focus', resetTicker);
}

export function set(ctrl: RoundController, text?: string): void {
  if (ctrl.data.player.spectator) return;
  if (!text) {
    if (aborted(ctrl.data) || finished(ctrl.data)) {
      text = i18n('gameOver');
    } else if (paused(ctrl.data)) {
      text = i18n('gameAdjourned');
    } else if (isPlayerTurn(ctrl.data)) {
      text = i18n('yourTurn');
      if (!document.hasFocus()) startTicker();
    } else {
      text = i18n('waitingForOpponent');
      resetTicker();
    }
  }
  document.title = `${text} - ${initialTitle}`;
}
