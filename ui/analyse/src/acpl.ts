import { defined } from 'common/common';
import { icons } from 'common/icons';
import { bind, dataIcon } from 'common/snabbdom';
import * as game from 'game';
import { i18n } from 'i18n';
import { engineNameFromCode } from 'shogi/engine-name';
import { h, thunk, type VNode, type VNodeData } from 'snabbdom';
import type AnalyseCtrl from './ctrl';
import { findTag } from './study/study-chapters';

type AdviceKind = 'inaccuracy' | 'mistake' | 'blunder';

interface Advice {
  kind: AdviceKind;
  name: string;
  symbol: string;
}

function renderRatingDiff(rd: number | undefined): VNode | undefined {
  if (rd === 0) return h('span', '±0');
  if (rd && rd > 0) return h('good', `+${rd}`);
  if (rd && rd < 0) return h('bad', `−${-rd}`);
  return;
}

function renderPlayer(ctrl: AnalyseCtrl, color: Color): VNode {
  const p = game.getPlayer(ctrl.data, color);
  if (p.user)
    return h(
      'a.user-link.ulpt',
      {
        attrs: { href: `/@/${p.user.username}` },
      },
      [p.user.username, ' ', renderRatingDiff(p.ratingDiff)],
    );
  return h(
    'span',
    p.name ||
      (p.ai && engineNameFromCode(p.aiCode, p.ai)) ||
      (ctrl.study && findTag(ctrl.study.data.chapter.tags, color)) ||
      h('span.anon', i18n('anonymousUser')),
  );
}

const advices: Advice[] = [
  { kind: 'inaccuracy', name: i18n('inaccuracies'), symbol: '?!' },
  { kind: 'mistake', name: i18n('mistakes'), symbol: '?' },
  { kind: 'blunder', name: i18n('blunders'), symbol: '??' },
];

function playerTable(ctrl: AnalyseCtrl, color: Color): VNode {
  const d = ctrl.data;
  const acpl = d.analysis![color].acpl;
  return h(
    'table',
    {
      hook: {
        insert(vnode) {
          window.lishogi.powertip.manualUserIn(vnode.elm);
        },
      },
    },
    [
      h(
        'thead',
        h('tr', [h('td', h(`i.is.color-icon.${color}`)), h('th', renderPlayer(ctrl, color))]),
      ),
      h(
        'tbody',
        advices
          .map(a => {
            const nb: number = d.analysis![color][a.kind];
            const style = nb ? `.symbol.${a.kind}` : '';
            const attrs: VNodeData = nb
              ? {
                  'data-color': color,
                  'data-symbol': a.symbol,
                }
              : {};
            return h(`tr${nb ? `.symbol${style}` : ''}`, { attrs }, [
              h('td', `${nb}`),
              h('th', a.name),
            ]);
          })
          .concat(
            h('tr', [
              h('td', `${defined(acpl) ? acpl : '?'}`),
              h('th', i18n('averageCentipawnLoss')),
            ]),
          ),
      ),
    ],
  );
}

function doRender(ctrl: AnalyseCtrl): VNode {
  return h(
    'div.advice-summary',
    {
      hook: {
        insert: vnode => {
          $(vnode.elm as HTMLElement).on('click', 'tr.symbol', function (this: Element) {
            ctrl.jumpToGlyphSymbol($(this).data('color'), $(this).data('symbol'));
          });
        },
      },
    },
    [
      playerTable(ctrl, 'sente'),
      ctrl.study
        ? null
        : h(
            'a.button.text',
            {
              class: { active: !!ctrl.retro },
              attrs: dataIcon(icons.play),
              hook: bind('click', ctrl.toggleRetro, ctrl.redraw),
            },
            i18n('learnFromYourMistakes'),
          ),
      playerTable(ctrl, 'gote'),
    ],
  );
}

export function render(ctrl: AnalyseCtrl): VNode | undefined {
  if (ctrl.studyPractice || ctrl.embed) return;

  if (
    !ctrl.data.analysis ||
    !ctrl.showComputer() ||
    (ctrl.study && ctrl.study.vm.toolTab() !== 'serverEval')
  )
    return h('div.analyse__acpl');

  // don't cache until the analysis is complete!
  const buster = ctrl.data.analysis.partial ? Math.random() : '';
  let cacheKey = `${buster}${!!ctrl.retro}`;
  if (ctrl.study) cacheKey += ctrl.study.data.chapter.id;

  return h('div.analyse__acpl', thunk('div.advice-summary', doRender, [ctrl, cacheKey]));
}
