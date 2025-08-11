import * as status from 'game/status';
import { i18n, i18nPluralSame } from 'i18n';
import { h, type VNode } from 'snabbdom';
import type { Millis } from '../clock/clock-ctrl';
import type { Position } from '../interfaces';
import { moretime } from '../view/button';
import type { CorresClockController } from './corres-clock-ctrl';

function prefixInteger(num: number, length: number): string {
  return (num / 10 ** length).toFixed(length).slice(2);
}

function bold(x: string) {
  return `<b>${x}</b>`;
}

function formatClockTime(time: Millis) {
  const date = new Date(time);
  const minutes = prefixInteger(date.getUTCMinutes(), 2);
  const seconds = prefixInteger(date.getSeconds(), 2);
  let hours: number;
  let str = '';
  if (time >= 86400 * 1000) {
    // days : hours
    const days = date.getUTCDate() - 1;
    hours = date.getUTCHours();
    str += `${days === 1 ? i18n('oneDay') : i18nPluralSame('nbDays', days)} `;
    if (hours !== 0) str += i18nPluralSame('nbHours', hours);
  } else if (time >= 3600 * 1000) {
    // hours : minutes
    hours = date.getUTCHours();
    str += `${bold(prefixInteger(hours, 2))}:${bold(minutes)}`;
  } else {
    // minutes : seconds
    str += `${bold(minutes)}:${bold(seconds)}`;
  }
  return str;
}

export default function (
  ctrl: CorresClockController,
  color: Color,
  position: Position,
  runningColor: Color,
): VNode {
  const millis = ctrl.millisOf(color);
  const update = (el: HTMLElement) => {
    el.innerHTML = formatClockTime(millis);
  };
  const isPlayer = ctrl.root.data.player.color === color;
  return h(
    `div.rclock.rclock-correspondence.rclock-${position}`,
    {
      class: {
        outoftime: millis <= 0,
        running: runningColor === color && !status.paused(ctrl.root.data),
      },
    },
    [
      h('div.time', {
        hook: {
          insert: vnode => update(vnode.elm as HTMLElement),
          postpatch: (_, vnode) => update(vnode.elm as HTMLElement),
        },
      }),
      isPlayer ? null : moretime(ctrl.root),
    ],
  );
}
