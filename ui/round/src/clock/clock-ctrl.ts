import { defined } from 'common/common';
import { type PrefTypes, prefs } from 'common/prefs';
import * as game from 'game';
import type { RoundData } from '../interfaces';
import { updateElements } from './clock-view';

export type Seconds = number;
export type Centis = number;
export type Millis = number;

interface ClockOpts {
  onFlag(): void;
  redraw: Redraw;
  nvui: boolean;
}

export interface ClockData {
  running: boolean;
  initial: Seconds;
  increment: Seconds;
  byoyomi: Seconds;
  periods: number;
  sente: Seconds;
  gote: Seconds;
  emerg: Seconds;
  moretime: number;
  sPeriods: number;
  gPeriods: number;
  pref: {
    showTenths: PrefTypes['clockTenths'];
    clockAudible: PrefTypes['clockAudible'];
    lowClockSound: boolean;
  };
}

interface Times {
  sente: Millis;
  gote: Millis;
  activeColor?: Color;
  lastUpdate: Millis;
}

type ColorMap<T> = { [C in Color]: T };

export interface ClockElements {
  time?: HTMLElement;
  clock?: HTMLElement;
}

interface EmergSound {
  lowtime(): void;
  nextPeriod(): void;
  lastByoTick?: number;
  next?: number;
  delay: Millis;
  playable: {
    sente: boolean;
    gote: boolean;
  };
}

export class ClockController {
  data: ClockData;

  emergSound: EmergSound = {
    lowtime: () => window.lishogi.sound.play('low-time', 'clock'),
    nextPeriod: () => window.lishogi.sound.play('byoyomi', 'clock'),
    delay: 20000,
    playable: {
      sente: true,
      gote: true,
    },
  };

  showTenths: (millis: Millis, color: Color) => boolean;
  times!: Times;

  playerColor: Color;
  isSpectator: boolean;
  emergMs: Millis;

  elements = {
    sente: {},
    gote: {},
  } as ColorMap<ClockElements>;

  byoyomi: number;
  initial: number;

  totalPeriods: number;
  curPeriods = {} as ColorMap<number>;
  goneBerserk = {} as ColorMap<boolean>;

  private tickCallback?: number;

  constructor(
    d: RoundData,
    readonly opts: ClockOpts,
  ) {
    this.data = d.clock!;

    if (this.data.pref.showTenths === prefs.clockTenths.NEVER) this.showTenths = () => false;
    else {
      const cutoff = this.data.pref.showTenths === prefs.clockTenths.LOWTIME ? 10000 : 3600000;
      this.showTenths = (time, color) =>
        time < cutoff &&
        (this.byoyomi === 0 ||
          time <= 1000 ||
          this.isUsingByo(color) ||
          this.data.pref.showTenths === prefs.clockTenths.ALWAYS);
    }

    this.byoyomi = this.data.byoyomi;
    this.initial = this.data.initial;

    this.totalPeriods = this.data.periods;
    this.curPeriods.sente = this.data.sPeriods ?? 0;
    this.curPeriods.gote = this.data.gPeriods ?? 0;

    this.goneBerserk[d.player.color] = !!d.player.berserk;
    this.goneBerserk[d.opponent.color] = !!d.opponent.berserk;

    this.isSpectator = !!d.player.spectator;
    this.playerColor = d.player.color;
    this.emergMs = 1000 * Math.min(60, Math.max(10, this.data.initial * 0.125));

    this.setClock(d, this.data.sente, this.data.gote, this.data.sPeriods, this.data.gPeriods);
  }

  isUsingByo = (color: Color): boolean =>
    this.byoyomi > 0 && (this.curPeriods[color] > 0 || this.initial === 0);

  setClock = (
    d: RoundData,
    sente: Seconds,
    gote: Seconds,
    sPer: number,
    gPer: number,
    delay: Centis = 0,
  ): void => {
    const isClockRunning = game.playable(d) && (game.playedPlies(d) > 1 || d.clock!.running);
    const delayMs = delay * 10;

    this.times = {
      sente: sente * 1000,
      gote: gote * 1000,
      activeColor: isClockRunning ? d.game.player : undefined,
      lastUpdate: performance.now() + delayMs,
    };
    this.curPeriods.sente = sPer;
    this.curPeriods.gote = gPer;

    if (isClockRunning) this.scheduleTick(this.times[d.game.player], d.game.player, delayMs);
  };

  addTime = (color: Color, time: Centis): void => {
    this.times[color] += time * 10;
    this.resetByoTicks();
  };

  setBerserk = (color: Color): void => {
    this.goneBerserk[color] = true;
  };

  canPlaySound = (color: Color): boolean => {
    return (
      this.data.pref.clockAudible === prefs.clockAudible.ALL ||
      (this.data.pref.clockAudible === prefs.clockAudible.MYGAME && !this.isSpectator) ||
      (color === this.playerColor && !this.isSpectator)
    );
  };

  nextPeriod = (color: Color): void => {
    this.curPeriods[color] += 1;
    this.times[color] += this.byoyomi * 1000;
    if (this.canPlaySound(color)) this.emergSound.nextPeriod();
    this.resetByoTicks();
  };

  stopClock = (): Millis | undefined => {
    const color = this.times.activeColor;
    if (color) {
      const curElapse = this.elapsed();
      this.times[color] = Math.max(0, this.times[color] - curElapse);
      this.times.activeColor = undefined;
      this.resetByoTicks();
      return curElapse;
    }
    return;
  };

  hardStopClock = (): void => {
    this.times.activeColor = undefined;
  };

  resetByoTicks = (): void => {
    this.emergSound.lastByoTick = undefined;
  };

  private scheduleTick = (time: Millis, color: Color, extraDelay: Millis) => {
    if (this.tickCallback !== undefined) clearTimeout(this.tickCallback);
    this.tickCallback = setTimeout(
      this.tick,
      // changing the value of active node confuses the chromevox screen reader
      // so update the clock less often
      this.opts.nvui
        ? 1000
        : (time %
            (this.showTenths(time, color) || (this.isUsingByo(color) && time < 11 * 1000)
              ? 100
              : 500)) +
            1 +
            extraDelay,
    );
  };

  // Should only be invoked by scheduleTick.
  private tick = (): void => {
    this.tickCallback = undefined;

    const color = this.times.activeColor;
    if (color === undefined) return;

    const now = performance.now();
    const millis = Math.max(0, this.times[color] - this.elapsed(now));
    const curPeriod = this.curPeriods[color];

    this.scheduleTick(millis, color, 0);
    if (
      millis === 0 &&
      !this.goneBerserk[color] &&
      this.byoyomi > 0 &&
      curPeriod < this.totalPeriods
    ) {
      this.nextPeriod(color);
      this.opts.redraw();
    } else if (millis === 0) this.opts.onFlag();
    else updateElements(this, this.elements[color], millis, color);

    if (this.canPlaySound(color)) {
      if (this.data.pref.lowClockSound && this.emergSound.playable[color]) {
        if (millis < this.emergMs && !(now < this.emergSound.next!) && !this.byoyomi) {
          this.emergSound.lowtime();
          this.emergSound.next = now + this.emergSound.delay;
          this.emergSound.playable[color] = false;
        }
      } else if (millis > 1.5 * this.emergMs) {
        this.emergSound.playable[color] = true;
      }

      const audioOffset = 400;
      const adjustedMillis = millis - audioOffset;

      if (
        millis > 0 &&
        this.isUsingByo(color) &&
        (!defined(this.emergSound.lastByoTick) ||
          Math.floor(adjustedMillis / 1000) < this.emergSound.lastByoTick)
      ) {
        this.emergSound.lastByoTick = Math.floor(adjustedMillis / 1000);

        // in seconds
        const remainingByo = Math.floor(adjustedMillis / 1000) + 1;
        const spentByo = this.byoyomi - remainingByo;

        if (this.byoyomi === remainingByo) return;

        if (window.lishogi.sound.clockSoundJapanese()) {
          // count up from 0 to 9
          if (remainingByo < 10) window.lishogi.sound.countdown(10 - remainingByo);
          // after 10, 20, 30, 40, 50 seconds elapsed
          else if (spentByo > 0 && spentByo % 10 === 0 && spentByo <= 50)
            window.lishogi.sound.play(`${spentByo}s`, 'clock');
        } else if (remainingByo) {
          if (window.lishogi.sound.clockSoundSet() === 'system' && remainingByo > 3) return;

          if (remainingByo <= 10) window.lishogi.sound.countdown(remainingByo);
          else if (remainingByo < 60 && remainingByo % 10 === 0)
            window.lishogi.sound.play(`${remainingByo}s`, 'clock');
        }
      }
    }
  };

  elapsed = (now: number = performance.now()): number => Math.max(0, now - this.times.lastUpdate);

  millisOf = (color: Color): Millis =>
    this.times.activeColor === color
      ? Math.max(0, this.times[color] - this.elapsed())
      : this.times[color];

  isRunning = (): boolean => this.times.activeColor !== undefined;
}
