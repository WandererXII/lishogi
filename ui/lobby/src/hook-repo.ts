import type LobbyController from './ctrl';
import type { Hook, Tab } from './interfaces';
import { withinRatingRange } from './util';

export const tabs: Tab[] = ['real_time', 'presets'];

const ratingOrder =
  (reverse: boolean) =>
  (a: Hook, b: Hook): number => {
    const ra = a.rating ?? 0;
    const rb = b.rating ?? 0;
    if (ra === rb) return 0;
    return (ra > rb ? -1 : 1) * (reverse ? -1 : 1);
  };

const timeOrder =
  (reverse: boolean) =>
  (a: Hook, b: Hook): number => {
    if (a.t === b.t) return 0;
    return (a.t > b.t ? -1 : 1) * (reverse ? -1 : 1);
  };

export function sort(ctrl: LobbyController, hooks: Hook[]): void {
  const s = ctrl.sort;
  hooks.sort(s.startsWith('time') ? timeOrder(s !== 'time') : ratingOrder(s !== 'rating'));
}

function init(ctrl: LobbyController, hook: Hook): void {
  if (!hook.variant) hook.variant = 'standard';

  if (hook.sri === window.lishogi.sri) hook.action = 'cancel';
  else {
    const isAuth = !!hook.u;
    const perfRating = ctrl.ratings?.[hook.perf];
    if ((!isAuth || !ctrl.isAnon) && (!perfRating || withinRatingRange(hook, perfRating.rating)))
      hook.action = 'join';
    else hook.action = 'unjoinable';
  }
}

export function add(ctrl: LobbyController, hook: Hook): boolean {
  init(ctrl, hook);

  const flushable =
    hook.action === 'cancel' ||
    (ctrl.data.hooks.length === ctrl.stepHooks.length &&
      ctrl.data.hooks.every(h => ctrl.stepHooks.some(s => s.id === h.id)));
  ctrl.data.hooks.push(hook);
  return flushable;
}

export function setAll(ctrl: LobbyController, hooks: Hook[]): void {
  hooks.forEach(h => {
    init(ctrl, h);
  });
  ctrl.data.hooks = hooks;
}

export function remove(ctrl: LobbyController, id: string): void {
  ctrl.data.hooks = ctrl.data.hooks.filter(h => h.id !== id);
  ctrl.stepHooks.forEach(h => {
    if (h.id === id) h.disabled = true;
  });
  if (ctrl.currentPresetId && !ctrl.data.hooks.some(h => h.sri === window.lishogi.sri))
    ctrl.currentPresetId = undefined;
}

export function syncIds(ctrl: LobbyController, ids: string[]): void {
  ctrl.data.hooks = ctrl.data.hooks.filter(h => ids.includes(h.id));
  if (ctrl.currentPresetId && !ctrl.data.hooks.some(h => h.sri === window.lishogi.sri))
    ctrl.currentPresetId = undefined;
}

export function find(ctrl: LobbyController, id: string): Hook | undefined {
  return ctrl.data.hooks.find(h => h.id === id);
}
