import * as game from 'game';
import type { Player } from 'game/interfaces';
import { ids } from 'game/status';
import { type Hooks, h, type VNode } from 'snabbdom';
import type RoundController from '../ctrl';
import type { Position } from '../interfaces';
import { bind, justIcon } from '../util';
import * as button from '../view/button';
import type { ClockController, ClockElements, Millis, Seconds } from './clock-ctrl';

export function renderClock(ctrl: RoundController, player: Player, position: Position): VNode {
  const clock = ctrl.clock!;
  const millis = clock.millisOf(player.color);
  const isPlayer = ctrl.data.player.color === player.color;
  const usingByo = clock.isUsingByo(player.color);
  const isRunning = player.color === clock.times.activeColor;
  const isOver = ctrl.data.game.status.id > ids.paused;

  const update = (el: HTMLElement) => {
    const els = clock.elements[player.color];
    const millis = clock.millisOf(player.color);
    const isRunning = player.color === clock.times.activeColor;
    els.time = el;
    els.clock = el.parentElement!;
    el.innerHTML = formatClockTime(
      millis,
      clock.showTenths(millis, player.color),
      isRunning,
      clock.opts.nvui,
    );
  };
  const timeHook: Hooks = {
    insert: vnode => update(vnode.elm as HTMLElement),
    postpatch: (_, vnode) => update(vnode.elm as HTMLElement),
  };
  return h(
    `div.rclock.rclock-${position}`,
    {
      class: {
        outoftime: millis <= 0,
        running: isRunning,
        over: isOver,
        byo: usingByo,
        emerg:
          (millis < clock.emergMs && clock.byoyomi === 0) ||
          (usingByo && millis < clock.byoEmergeS * 1000),
      },
    },
    clock.opts.nvui
      ? [
          h('div.clock-byo', [
            h('div.time', {
              attrs: { role: 'timer' },
              hook: timeHook,
            }),
          ]),
        ]
      : [
          h('div.clock-byo', [
            h('div.time', {
              class: {
                hour: millis > 3600 * 1000,
              },
              hook: timeHook,
            }),
            renderByoyomiTime(
              clock.byoyomi,
              clock.totalPeriods - clock.curPeriods[player.color],
              ctrl.goneBerserk[player.color],
            ),
          ]),
          renderBerserk(ctrl, player.color, position),
          isPlayer ? goBerserk(ctrl) : button.moretime(ctrl),
          tourRank(ctrl, player.color, position),
        ],
  );
}

function pad2(num: number): string {
  return (num < 10 ? '0' : '') + num;
}

function renderByoyomiTime(byoyomi: Seconds, periods: number, berserk = false) {
  const perStr = periods > 1 ? `(${periods}x)` : '';
  return h(
    `div.byoyomi.per${periods}`,
    { berserk: berserk },
    !berserk && byoyomi && periods ? [h('span', '|'), `${byoyomi}s${perStr}`] : '',
  );
}

const sepHigh = '<sep>:</sep>';
const sepLow = '<sep class="low">:</sep>';

function formatClockTime(time: Millis, showTenths: boolean, isRunning: boolean, nvui: boolean) {
  const date = new Date(time);
  if (nvui)
    return `${
      (time >= 3600000 ? `${Math.floor(time / 3600000)}H:` : '') + date.getUTCMinutes()
    }M:${date.getUTCSeconds()}S`;
  const millis = date.getUTCMilliseconds();
  const sep = isRunning && millis < 500 ? sepLow : sepHigh;
  const baseStr = pad2(date.getUTCMinutes()) + sep + pad2(date.getUTCSeconds());
  if (time >= 3600000) {
    const hours = pad2(Math.floor(time / 3600000));
    return hours + sepHigh + baseStr;
  } else if (showTenths) {
    let tenthsStr = Math.floor(millis / 100).toString();
    if (!isRunning && time < 1000) {
      tenthsStr += `<huns>${Math.floor(millis / 10) % 10}</huns>`;
    }

    return `${baseStr}<tenths><sep>.</sep>${tenthsStr}</tenths>`;
  } else {
    return baseStr;
  }
}

export function updateElements(
  clock: ClockController,
  els: ClockElements,
  millis: Millis,
  color: Color,
): void {
  if (els.time)
    els.time.innerHTML = formatClockTime(
      millis,
      clock.showTenths(millis, color),
      true,
      clock.opts.nvui,
    );
  if (els.clock?.parentElement) {
    const cl = els.clock.parentElement.classList;
    if (
      (millis < clock.emergMs && clock.byoyomi === 0) ||
      (clock.isUsingByo(color) && millis < clock.byoEmergeS * 1000)
    )
      cl.add('emerg');
    else if (cl.contains('emerg')) cl.remove('emerg');
  }
}

function showBerserk(ctrl: RoundController, color: Color): boolean {
  return !!ctrl.goneBerserk[color] && ctrl.data.game.plies <= 1 && game.playable(ctrl.data);
}

function renderBerserk(ctrl: RoundController, color: Color, position: Position) {
  return showBerserk(ctrl, color) ? h(`div.berserked.${position}`, justIcon('`')) : null;
}

function goBerserk(ctrl: RoundController) {
  if (!game.berserkableBy(ctrl.data)) return;
  if (ctrl.goneBerserk[ctrl.data.player.color]) return;
  return h('button.fbt.go-berserk', {
    attrs: {
      title: 'GO BERSERK! Half the time, no increment, no byoyomi, bonus point',
      'data-icon': '`',
    },
    hook: bind('click', ctrl.goBerserk),
  });
}

function tourRank(ctrl: RoundController, color: Color, position: Position) {
  const d = ctrl.data;
  const ranks = d.tournament?.ranks;
  return ranks && !showBerserk(ctrl, color)
    ? h(
        `div.tour-rank.${position}`,
        {
          attrs: { title: 'Current tournament rank' },
        },
        `#${ranks[color]}`,
      )
    : null;
}
