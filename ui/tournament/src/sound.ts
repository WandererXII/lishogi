import notify from 'common/notification';
import { once } from 'common/storage';
import type { TournamentDataFull } from './interfaces';

let countDownTimeout: number | undefined;
const li = window.lishogi;

function doCountDown(targetTime: number) {
  let started = false;

  return function curCounter() {
    const secondsToStart = (targetTime - performance.now()) / 1000;

    // always play the 0 sound before completing.
    const bestTick = Math.max(0, Math.round(secondsToStart));
    if (bestTick <= 10) {
      const key = `countDown${bestTick}`;
      console.info(key, new Date());
      li.sound.play(key);
    }

    if (bestTick > 0) {
      const nextTick = Math.min(10, bestTick - 1);
      countDownTimeout = setTimeout(
        curCounter,
        1000 * Math.min(1.1, Math.max(0.8, secondsToStart - nextTick)),
      );
    }

    if (!started && bestTick <= 10) {
      started = true;
      notify('The tournament is starting!');
    }
  };
}

export function end(data: TournamentDataFull): void {
  if (!data.me) return;
  if (!data.isRecentlyFinished) return;
  if (!once(`tournament.end.sound.${data.id}`)) return;

  let soundKey = 'Other';
  if (data.me.rank < 4) soundKey = '1st';
  else if (data.me.rank < 11) soundKey = '2nd';
  else if (data.me.rank < 21) soundKey = '3rd';

  const soundName = `tournament${soundKey}`;
  li.sound.loadStandard(soundName);
  li.sound.play(soundName);
}

export function countDown(data: TournamentDataFull): void {
  if (!data.me || !data.secondsToStart) {
    if (countDownTimeout) clearTimeout(countDownTimeout);
    countDownTimeout = undefined;
    return;
  }
  if (countDownTimeout) return;
  if (data.secondsToStart > 60 * 60 * 24) return;

  countDownTimeout = setTimeout(
    doCountDown(performance.now() + 1000 * data.secondsToStart - 100),
    900,
  ); // wait 900ms before starting countdown.

  // Preload countdown sounds.
  for (let i = 10; i >= 0; i--) li.sound.loadStandard(`countDown${i}`);
}
