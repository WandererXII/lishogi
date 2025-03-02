import { loadCssPath } from 'common/assets';
import { debounce } from 'common/timings';
import type { BoardElements } from 'shogiground/types';

type MouchEvent = MouseEvent & TouchEvent;

export default function resizeHandle(
  els: BoardElements,
  pref: number,
  conf: {
    ply?: number;
    initialPly?: number;
    visible?: () => boolean;
  },
): void {
  if (!pref) return;

  const el = document.createElement('sg-resize');
  els.board.appendChild(el);

  const startResize = (start: MouchEvent) => {
    start.preventDefault();

    const mousemoveEvent = start.type === 'touchstart' ? 'touchmove' : 'mousemove';
    const mouseupEvent = start.type === 'touchstart' ? 'touchend' : 'mouseup';

    const startPos = eventPosition(start)!;
    const initialZoom = Number.parseInt(
      getComputedStyle(document.documentElement).getPropertyValue('--zoom'),
    );
    let zoom = initialZoom;

    const saveZoom = debounce(() => {
      window.lishogi.xhr.text('POST', '/pref/zoom', { url: { v: 100 + zoom } });
    }, 700);

    const resize = (move: MouchEvent) => {
      const pos = eventPosition(move)!;
      const delta = pos[0] - startPos[0] + pos[1] - startPos[1];

      zoom = Math.round(Math.min(100, Math.max(0, initialZoom + delta / 10)));

      document.documentElement.style.setProperty('--zoom', zoom.toString());
      window.dispatchEvent(new Event('resize'));

      saveZoom();
    };

    document.body.classList.add('resizing');

    document.addEventListener(mousemoveEvent, resize);

    document.addEventListener(
      mouseupEvent,
      () => {
        document.removeEventListener(mousemoveEvent, resize);
        document.body.classList.remove('resizing');
      },
      { once: true },
    );
  };

  el.addEventListener('touchstart', startResize, { passive: false });
  el.addEventListener('mousedown', startResize, { passive: false });

  if (pref == 1) {
    const toggle = (ply: number) =>
      el.classList.toggle(
        'none',
        conf.visible ? !conf.visible() : ply - (conf.initialPly || 0) >= 2,
      );
    toggle(conf.ply || 0);
    window.lishogi.pubsub.on('ply', toggle);
  }

  addNag(el);
}

function eventPosition(e: MouchEvent): [number, number] | undefined {
  if (e.clientX || e.clientX === 0) return [e.clientX, e.clientY];
  if (e.touches && e.targetTouches[0])
    return [e.targetTouches[0].clientX, e.targetTouches[0].clientY];
  return undefined;
}

function addNag(el: HTMLElement) {
  const storage = window.lishogi.storage.makeBoolean('resize-nag');
  if (storage.get()) return;

  loadCssPath('misc.nag-circle');
  el.title = 'Drag to resize';
  el.innerHTML = '<div class="nag-circle"></div>';
  for (const mousedownEvent of ['touchstart', 'mousedown']) {
    el.addEventListener(
      mousedownEvent,
      () => {
        storage.set(true);
        el.innerHTML = '';
      },
      { once: true },
    );
  }

  setTimeout(() => storage.set(true), 15000);
}
