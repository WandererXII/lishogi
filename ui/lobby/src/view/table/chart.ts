import { getPerfIcon } from 'common/perf-icons';
import { bind } from 'common/snabbdom';
import { i18n, i18nPluralSame } from 'i18n';
import { h, type VNode } from 'snabbdom';
import type LobbyController from '../../ctrl';
import type { Hook, Seek } from '../../interfaces';
import { action, isHook } from '../../util';

function percents(v: number) {
  return `${v}%`;
}

function renderPlot(ctrl: LobbyController, hs: Hook | Seek): VNode {
  const bottom = Math.max(0, yCoord(hs));
  const left = Math.max(0, xCoord(hs));
  const act = action(hs);
  const klass = ['plot.new', (isHook(hs) ? hs.ra : hs.mode) ? 'rated' : 'casual', act].join('.');
  return h(`span#${hs.id}.${klass}`, {
    key: hs.id,
    attrs: {
      'data-icon': getPerfIcon(hs.perf || hs.variant || 'standard'),
      style: `bottom:${percents(bottom)};left:${percents(left)}`,
    },
    hook: {
      insert(vnode) {
        $(vnode.elm as HTMLElement)
          .powerTip({
            intentPollInterval: 100,
            placement: `${bottom > 75 ? 's' : 'n'}${left > 75 ? 'w' : 'e'}`,
            mouseOnToPopup: true,
            closeDelay: 200,
            popupId: 'hook',
          })
          .data('powertipjq', $(renderPowertip(hs)))
          .on({
            powerTipRender() {
              $('#hook .inner-clickable').on('click', () => {
                ctrl.clickHook(hs.id);
              });
            },
          });
        setTimeout(() => {
          (vnode.elm as HTMLElement).classList.remove('new');
        }, 20);
      },
      destroy(vnode) {
        $(vnode.elm as HTMLElement).data('powertipjq', null);
        $.powerTip.destroy(vnode.elm as HTMLElement);
      },
    },
  });
}

function renderPowertip(hs: Hook | Seek): string {
  const color = (isHook(hs) ? hs.c : hs.color) || 'random';
  let html = '<div class="inner">';
  if (hs.rating) {
    const usr = isHook(hs) ? hs.u : hs.username;
    html += `<a class="opponent ulpt is color-icon ${color}" href="/@/${usr}">`;
    html += ` ${usr} (${hs.rating}${(isHook(hs) ? hs.prov : hs.provisional) ? '?' : ''})`;
    html += '</a>';
  } else {
    html += `<span class="opponent anon ${color}">${i18n('anonymousUser')}</span>`;
  }
  html += '<div class="inner-clickable">';
  html += `<div>${isHook(hs) ? hs.clock : hs.days ? i18nPluralSame('nbDays', hs.days) : 'INF'}</div>`;
  html += `<i data-icon="${getPerfIcon(hs.perf || hs.variant || 'standard')}"> ${(isHook(hs) ? hs.ra : hs.mode) ? i18n('rated') : i18n('casual')}</i>`;
  html += '</div>';
  html += '</div>';
  return html;
}

const xMarksHook = ['', '', '', '', '', '', '', '', ''];
const xMarksSeek = [1, 2, 3, 5, 7, 10, 14];

function xCoord(hs: Hook | Seek) {
  if (isHook(hs)) {
    const left = (hs.t * 90) / 3000 + 5;
    return Math.max(Math.min(left, 95), 0);
  } else {
    const oneSlice = 100 / (xMarksSeek.length + 1);
    const index = (xMarksSeek.findIndex(x => x == hs.days) || 14) + 1;
    return Math.min(oneSlice * index - 2, 95);
  }
}

function renderXAxis(tab: 'seeks' | 'real_time') {
  const tags: VNode[] = [];
  const values = tab === 'seeks' ? xMarksSeek : xMarksHook;
  const oneSlice = 100 / (values.length + 1);
  values.forEach((v, i) => {
    tags.push(
      h(
        'span.x.label',
        {
          attrs: { style: `left:${percents(oneSlice * (i + 1))}` },
        },
        `${v}`,
      ),
    );
    tags.push(
      h('div.grid.vert', {
        attrs: { style: `width:${percents(oneSlice * (i + 1))}` },
      }),
    );
  });
  return tags;
}

const yMarks = [1000, 1200, 1400, 1600, 1800, 2000];

function yCoord(hs: Hook | Seek): number {
  const isRated = isHook(hs) ? hs.ra : hs.mode;
  const r = hs.rating || 1500;
  const offset = 100 / (yMarks.length + 1);

  let height: number;
  if (r < 1000) height = ((r - 500) * 10) / 500;
  else if (r > 2000) height = yMarks.length * offset + ((r - 2000) * 10) / 500;
  else height = offset + ((r - 1000) * (100 - 2 * offset)) / 1000;

  return Math.max(Math.min(height, 95), 2) + (isRated ? 1.5 : -1.5);
}

function renderYAxis() {
  const tags: VNode[] = [];
  const oneSlice = 100 / (yMarks.length + 1);
  yMarks.forEach((v, i) => {
    tags.push(
      h(
        'span.y.label',
        {
          attrs: { style: `bottom:${percents(oneSlice * (i + 1))}` },
        },
        `${v}`,
      ),
    );
    tags.push(
      h('div.grid.horiz', {
        attrs: { style: `height:${percents(oneSlice * (i + 1))}` },
      }),
    );
  });
  return tags;
}

export function toggle(ctrl: LobbyController): VNode {
  return h('i.toggle', {
    key: 'set-mode-list',
    attrs: { title: i18n('list'), 'data-icon': '?' },
    hook: bind('mousedown', _ => ctrl.setMode('list'), ctrl.redraw),
  });
}

export function render(
  tab: 'seeks' | 'real_time',
  ctrl: LobbyController,
  hss: Seek[] | Hook[],
): VNode {
  // filter out overlappping seeks
  if (tab === 'seeks') {
    const seen: string[] = [];
    hss = (hss as Seek[]).filter(h => {
      const hash = (h.days || 'INF') + h.mode.toString() + h.rating;
      if (seen.includes(hash)) return false;
      seen.push(hash);
      return true;
    });
  }
  return h('div.hooks__chart', [
    h(
      'div.canvas',
      {
        hook: bind(
          'click',
          e => {
            if ((e.target as HTMLElement).classList.contains('plot'))
              ctrl.clickHook((e.target as HTMLElement).id);
          },
          ctrl.redraw,
        ),
      },
      hss.map(hook => renderPlot(ctrl, hook)),
    ),
    ...renderYAxis(),
    ...renderXAxis(tab),
  ]);
}
