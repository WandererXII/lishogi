import { clockEstimateSeconds } from 'common/clock';
import { debounce } from 'common/timings';
import { idToVariant, variantToId } from 'common/variant';
import { RULES } from 'shogiops/constants';
import { findHandicap, findHandicaps, isHandicap } from 'shogiops/handicaps';
import { parseSfen } from 'shogiops/sfen';
import type LobbyController from '../ctrl';
import { type FormStore, makeStore } from '../form';
import {
  aiLevelChoices,
  byoChoices,
  dayChoices,
  dayExtraChoices,
  incChoices,
  Mode,
  maxRatingChoices,
  minRatingChoices,
  modeChoices,
  Position,
  periodChoices,
  positionChoices,
  TimeMode,
  timeChoices,
  timeModeChoices,
} from './util';

export default class SetupCtrl {
  key!: SetupKey;
  data!: SetupData;

  stores: {
    hook: FormStore;
    friend: FormStore;
    ai: FormStore;
  };

  isOpen = false;
  isExtraOpen = false;
  submitted = false;

  invalidSfen = false;
  error: string | undefined;

  nvui: boolean;

  constructor(public root: LobbyController) {
    this.stores = {
      hook: makeStore(window.lishogi.storage.make('lobby.setup.hook')),
      friend: makeStore(window.lishogi.storage.make('lobby.setup.friend')),
      ai: makeStore(window.lishogi.storage.make('lobby.setup.ai')),
    };
    this.nvui = root.opts.blindMode;
  }

  redraw(): void {
    this.root.redraw();
  }

  set<K extends SetupDataKey>(key: K, value: SetupData[K]): void {
    if (key === 'handicap') {
      this.data.sfen = value as string;
      this.invalidSfen = false;
      this.redraw();
    } else if (key === 'sfen') {
      this.data.handicap =
        findHandicap({
          rules: this.variantKey(),
          sfen: value as SetupData['sfen'],
        })?.sfen || '';
      this.validateSfen();
    } else if (key === 'variant') {
      this.data.sfen = '';
      this.data.handicap = '';
      this.data.position = Position.initial;
      this.invalidSfen = false;
    }

    this.data[key] = value;

    this.updateData();

    this.redraw();
  }

  selected<K extends SetupDataKey>(key: K): SetupData[K] {
    return this.data[key];
  }

  isCorres(): boolean {
    return this.data.timeMode == TimeMode.Corres;
  }

  hasSfen(): boolean {
    return !!this.data.sfen && this.data.position === Position.fromPosition;
  }

  isHandicap(): boolean {
    return this.data.position === Position.fromPosition && this.data.sfen
      ? isHandicap({ sfen: this.data.sfen, rules: this.variantKey() })
      : false;
  }

  validateSfen: () => void = debounce(() => {
    if (this.hasSfen()) {
      const validated = parseSfen(this.variantKey(), this.data.sfen, true);
      if (validated.isOk) {
        this.invalidSfen = false;
        this.save();
        this.redraw();
      } else {
        this.invalidSfen = true;
        this.redraw();
      }
    }
  }, 300);

  variantKey(): VariantKey {
    return idToVariant(this.data.variant);
  }

  timeSum(): number {
    return clockEstimateSeconds(
      this.data.time * 60,
      this.data.byoyomi,
      this.data.increment,
      this.data.periods,
    );
  }

  canBeRated(): boolean {
    return !(
      this.hasSfen() ||
      (this.key === 'hook' && this.data.timeMode == TimeMode.Unlimited) ||
      (this.data.timeMode == TimeMode.RealTime &&
        (this.data.periods > 1 || (this.data.increment > 0 && this.data.byoyomi > 0))) ||
      (this.data.variant == 3 && this.timeSum() < 250)
    );
  }

  validTime(): boolean {
    return (
      this.data.timeMode !== TimeMode.RealTime ||
      ((this.data.time > 0 || this.data.increment > 0 || this.data.byoyomi > 0) &&
        (this.data.byoyomi > 0 || this.data.periods === 1))
    );
  }

  canChooseColor(): boolean {
    return this.key !== 'hook' || this.data.mode !== Mode.Rated;
  }

  isAnon(): boolean {
    return !document.body.dataset.user;
  }

  canSubmit(): boolean {
    const timeOk = this.validTime();
    const ratedOk = this.data.mode != Mode.Rated || this.canBeRated();
    const aiOk =
      this.key !== 'ai' ||
      this.data.variant === 1 ||
      this.data.time >= 1 ||
      this.data.byoyomi >= 10 ||
      this.data.increment >= 5;
    const sfenOk = this.data.position === Position.initial || !this.invalidSfen;

    return !this.submitted && !this.invalidSfen && timeOk && ratedOk && aiOk && sfenOk;
  }

  perf(): Perf | undefined {
    if (!this.validTime()) return;
    const v = this.variantKey();
    if (v === 'standard') {
      if (this.isCorres()) return 'correspondence';
      else return 'realTime';
    } else return v;
  }

  rating(): number | undefined {
    if (!this.root.ratings) return;

    const p = this.perf();
    return p ? this.root.ratings[p]?.rating : undefined;
  }

  ratingRange(): string | undefined {
    const rating = this.rating();
    if (rating) return `${rating - this.data.ratingMin}-${rating + this.data.ratingMax}`;
    else return;
  }

  initData(key: SetupKey, extraData?: Record<string, string>): void {
    this.key = key;

    const store = this.stores[this.key]?.get() || {};

    const getNumber = (k: keyof SetupData, options: number[]): number => {
      const extra = extraData?.[k] ? Number.parseInt(extraData[k]) : undefined;
      const saved = extra ?? Number.parseInt(store[k]);
      if (saved !== null && saved !== undefined && !isNaN(saved) && options.includes(saved))
        return saved;
      else return SetupCtrl.defaultData[k] as number;
    };

    const getString = (k: keyof SetupData, options: string[] | undefined = undefined): string => {
      const extra = extraData?.[k];
      const saved = extra ?? store[k];
      if (saved !== null && saved !== undefined && (!options || options.includes(saved)))
        return saved;
      else return SetupCtrl.defaultData[k] as string;
    };

    try {
      const variantId = getNumber(
        'variant',
        RULES.map(r => variantToId(r)),
      );
      const timeMode = this.isAnon() ? TimeMode.RealTime : getNumber('timeMode', timeModeChoices);

      this.data = {
        variant: variantId,
        timeMode,
        time: getNumber('time', timeChoices),
        byoyomi: getNumber('byoyomi', byoChoices),
        increment: getNumber('increment', incChoices),
        periods: getNumber('periods', periodChoices),
        days: getNumber('days', key === 'hook' ? dayChoices : dayExtraChoices),
        position: getNumber('position', positionChoices),
        sfen: getString('sfen'),
        handicap: getString(
          'sfen',
          findHandicaps({ rules: idToVariant(variantId) }).map(h => h.sfen),
        ),
        level: getNumber('level', aiLevelChoices),
        ratingMin: getNumber('ratingMin', minRatingChoices),
        ratingMax: getNumber('ratingMax', maxRatingChoices),
        mode: getNumber('mode', modeChoices),
        proMode: false, // for now
        user: extraData?.user,
      };
    } catch (e) {
      console.error('Failed to parse saved form data', e);
      this.data = SetupCtrl.defaultData;
    }

    this.updateData();

    this.validateSfen();

    this.isExtraOpen = this.data.periods > 1 || this.data.increment > 0;
  }

  updateData(): void {
    if (!this.canBeRated()) this.data.mode = Mode.Casual;
  }

  open(key: SetupKey, extraData?: Record<string, string>): void {
    this.initData(key, extraData);

    this.isOpen = true;
    this.error = undefined;
    this.submitted = false;
    this.redraw();
  }

  close(): void {
    this.save();
    this.isOpen = false;
    document.querySelectorAll('.lobby__start button.active').forEach(el => {
      el.classList.remove('active');
    });
    this.redraw();
  }

  toggleExtra(): void {
    this.isExtraOpen = !this.isExtraOpen;

    if (!this.isExtraOpen) {
      this.data.periods = 1;
      this.data.increment = 0;
    }

    this.redraw();
  }

  save = (): void => {
    this.stores[this.key].set(this.data as any);
  };

  submit = (color: Color | 'random'): void => {
    this.error = undefined;
    this.submitted = true;
    this.redraw();

    const isUnlimited = this.data.timeMode === TimeMode.Corres && this.data.days === 0;
    const postData = {
      variant: this.data.variant,
      timeMode: isUnlimited ? TimeMode.Unlimited : this.data.timeMode,
      time: this.data.time,
      byoyomi: this.data.byoyomi,
      increment: this.data.increment,
      periods: this.data.periods,
      days: Math.max(this.data.days, 1),
      sfen: this.data.position === Position.fromPosition ? this.data.sfen : '',
      level: this.data.level,
      mode: this.data.mode,
      proMode: this.data.proMode,
      ratingRange: this.ratingRange(),
      color: color,
    };

    let url = `/setup/${this.key}`;
    if (this.key === 'hook') url += `/${window.lishogi.sri}`;

    window.lishogi.xhr
      .json('POST', url, {
        url: {
          redirect: true,
          user: this.data.user && this.key === 'friend' ? this.data.user : undefined,
        },
        formData: postData,
      })
      .then(data => {
        if (this.key === 'hook') {
          this.root.setTab(this.isCorres() ? 'seeks' : 'real_time');
          this.close();
        } else if (this.key === 'friend' || this.key === 'ai') window.lishogi.redirect(data);
      })
      .catch(error => {
        this.submitted = false;
        try {
          const res = error as Response;
          res
            .json()
            .then((body: any) => {
              this.error = JSON.stringify(body.error?.global || body.error);
              this.redraw();
            })
            .catch(() => {
              console.error('Error parsing:', res);
              this.error = res.statusText;
              this.redraw();
            });
        } catch {
          console.error(error);
        }
      });
  };

  static defaultData: SetupData = {
    variant: variantToId('standard'),
    timeMode: TimeMode.RealTime,
    time: 30,
    byoyomi: 10,
    increment: 0,
    periods: 1,
    days: 1,
    position: Position.initial,
    handicap: '',
    sfen: '',
    level: 1,
    ratingMin: 500,
    ratingMax: 500,
    mode: Mode.Casual,
    proMode: false,
    user: undefined,
  };
}

export type SetupKey = 'hook' | 'friend' | 'ai';

interface SetupData {
  variant: number;
  timeMode: number;
  time: number;
  byoyomi: number;
  increment: number;
  periods: number;
  days: number;
  position: number;
  sfen: string;
  handicap: string;
  level: number;
  mode: number;
  proMode: boolean;
  ratingMin: number;
  ratingMax: number;
  user: string | undefined;
}

export type SetupDataKey = keyof SetupData;
