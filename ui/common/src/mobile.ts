import type { Hooks } from 'snabbdom';
import { bind } from './snabbdom';

const longPressDuration = 610;

export function bindMobileTapHold(
  el: HTMLElement,
  f: (e: Event) => unknown,
  redraw?: () => void,
): void {
  let longPressCountdown: number;
  const passive = { passive: true };

  el.addEventListener(
    'touchstart',
    e => {
      longPressCountdown = setTimeout(() => {
        f(e);
        redraw?.();
      }, longPressDuration);
    },
    passive,
  );

  el.addEventListener(
    'touchmove',
    () => {
      clearTimeout(longPressCountdown);
    },
    passive,
  );

  el.addEventListener(
    'touchcancel',
    () => {
      clearTimeout(longPressCountdown);
    },
    passive,
  );

  el.addEventListener(
    'touchend',
    () => {
      clearTimeout(longPressCountdown);
    },
    passive,
  );
}

export function bindMobileMousedown(
  el: HTMLElement,
  f: (e: Event) => unknown,
  redraw?: () => void,
): void {
  for (const mousedownEvent of ['pointerdown']) {
    el.addEventListener(
      mousedownEvent,
      e => {
        f(e);
        e.preventDefault();
        if (redraw) redraw();
      },
      { passive: false },
    );
  }
}

export function hookMobileMousedown(f: (e: Event) => any): Hooks {
  return bind('ontouchstart' in window ? 'click' : 'mousedown', f);
}

export const isAndroid = (): boolean => /Android/.test(navigator.platform);

export const isIOS = (): boolean => /iPhone|iPod/.test(navigator.platform) || isIPad();

// some newer iPads pretend to be Macs, hence checking for "Macintosh"
export const isIPad = (): boolean =>
  navigator?.maxTouchPoints > 2 && /iPad|Macintosh/.test(navigator.userAgent);

export const hasTouchEvents: boolean = 'ontouchstart' in window;

let isCol1Cache: 'init' | 'rec' | boolean = 'init';
export function isCol1(): boolean {
  if (typeof isCol1Cache == 'string') {
    if (isCol1Cache == 'init') {
      // only once
      window.addEventListener('resize', () => {
        isCol1Cache = 'rec';
      }); // recompute on resize
      if (navigator.userAgent.indexOf('Edge/') > -1)
        // edge gets false positive on page load, fix later
        requestAnimationFrame(() => {
          isCol1Cache = 'rec';
        });
    }
    isCol1Cache = !!getComputedStyle(document.body).getPropertyValue('--col1');
  }
  return isCol1Cache;
}

let hoverable: boolean | undefined;
export function isHoverable(): boolean {
  if (hoverable === undefined)
    hoverable =
      !hasTouchEvents /* Firefox <= 63 */ ||
      !!getComputedStyle(document.body).getPropertyValue('--hoverable');
  return hoverable;
}
