import { assetUrl, loadCss } from 'common/assets';
import { i18n } from 'i18n';
import { i18nVariant } from 'i18n/variant';
import { h, type VNode } from 'snabbdom';
import { bind, type Close, header } from './util';

type PieceSetKey = string;
type PieceSet = {
  key: PieceSetKey;
  name: string;
  pro?: boolean;
};
type Tab = 'standard' | 'chushogi' | 'kyotoshogi';

export interface PieceSetData {
  current: PieceSetKey;
  list: PieceSet[];
}

export interface PieceCtrl {
  std: PieceSetData;
  chu: PieceSetData;
  kyo: PieceSetData;
  activeTab: Tab;
  setActiveTab(s: Tab): void;
  set(t: PieceSetKey): void;
  close: Close;
}

export function ctrl(
  std: PieceSetData,
  chu: PieceSetData,
  kyo: PieceSetData,
  redraw: Redraw,
  close: Close,
): PieceCtrl {
  const isChushogi = !!document.body.querySelector('.main-v-chushogi');
  const isKyotoshogi = !isChushogi && !!document.body.querySelector('.main-v-kyotoshogi');
  const initialTab = isChushogi ? 'chushogi' : isKyotoshogi ? 'kyotoshogi' : 'standard';
  loadCssPieces(initialTab);
  return {
    std: std,
    chu: chu,
    kyo: kyo,
    activeTab: initialTab,
    setActiveTab(s: Tab) {
      this.activeTab = s;
      loadCssPieces(this.activeTab);
      redraw();
      if (s !== 'standard') window.scrollTo(0, 0);
    },
    set(key: PieceSetKey) {
      if (this.activeTab === 'chushogi') chu.current = key;
      else if (this.activeTab === 'kyotoshogi') kyo.current = key;
      else std.current = key;

      applyPiece(key, this.activeTab);

      const prefix = variantPrefix(this.activeTab);
      const path = prefix ? `${prefix}PieceSet` : 'pieceSet';
      window.lishogi.xhr
        .text('POST', `/pref/${path}`, {
          formData: { set: key },
        })
        .catch(() => window.lishogi.announce({ msg: 'Failed to save piece set preference' }));
      redraw();
    },
    close,
  };
}

export function view(ctrl: PieceCtrl): VNode {
  return h('div.sub.piece', [
    header(i18n('pieceSet'), () => ctrl.close()),
    h(
      'a.categ-tabs',
      {
        hook: bind('click', e => {
          const tab = ((e.target as HTMLElement).dataset.tab || 'standard') as Tab;
          ctrl.setActiveTab(tab);
        }),
      },
      (['standard', 'chushogi', 'kyotoshogi'] as const).map(v =>
        h(
          'div',
          { attrs: { 'data-tab': v }, class: { active: ctrl.activeTab === v } },
          i18nVariant(v),
        ),
      ),
    ),
    h(
      'div.list-wrap',
      {
        hook: bind('click', e => {
          const pieceSet = (e.target as HTMLElement).dataset.value;
          if (pieceSet) ctrl.set(pieceSet);
        }),
      },
      h(
        `div.list.tab-${ctrl.activeTab}`,
        {
          key: ctrl.activeTab,
        },
        ctrl.activeTab === 'chushogi'
          ? ctrl.chu.list.map(pieceView(ctrl.chu.current))
          : ctrl.activeTab === 'kyotoshogi'
            ? ctrl.kyo.list.map(pieceView(ctrl.kyo.current))
            : ctrl.std.list.map(pieceView(ctrl.std.current)),
      ),
    ),
  ]);
}

function pieceView(current: PieceSetKey) {
  return (p: PieceSet) =>
    h(
      'a.no-square',
      {
        attrs: { title: p.name, 'data-value': p.key },
        class: { active: current === p.key, 'not-pro': !p.pro },
      },
      h('piece'),
    );
}

function applyPiece(key: PieceSetKey, tab: Tab) {
  const prefix = variantPrefix(tab);
  const sprite = $(`#${prefix ? `${prefix}-` : ''}piece-sprite`);
  if (sprite.length) sprite.attr('href', sprite.attr('href')!.replace(/\w+\.css/, `${key}.css`));

  if (tab === 'chushogi') document.body.dataset.chuPieceSet = key;
  else if (tab === 'kyotoshogi') document.body.dataset.kyoPieceSet = key;
  else document.body.dataset.pieceSet = key;
}

function loadCssPieces(tab: Tab): void {
  loadCss(assetUrl(`piece-css/${tab}/lishogi.dasher.css`));
}

function variantPrefix(tab: Tab): string {
  return tab === 'standard' ? '' : tab.slice(0, 3);
}
