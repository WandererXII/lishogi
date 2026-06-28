import * as cevalView from 'ceval/view';
import { boardControls } from 'common/board-controls';
import { icons } from 'common/icons';
import { bindNonPassive } from 'common/snabbdom';
import stepwiseScroll from 'common/wheel';
import { i18n } from 'i18n';
import { render as renderKeyboardMove } from 'keyboard-move';
import { h, type VNode } from 'snabbdom';
import * as control from '../control';
import type { Controller } from '../interfaces';
import feedbackView from './feedback';
import * as shogiground from './shogiground';
import * as side from './side';
import theme from './theme';
import { render as treeView } from './tree';

function renderAnalyse(ctrl: Controller): VNode {
  return h('div.puzzle__moves.areplay', [treeView(ctrl)]);
}

// to prevent accidental double clicks
let loading: Timeout | undefined;

function controls(ctrl: Controller): VNode {
  const node = ctrl.vm.node;
  const nextNode = node.children[0];
  const goNext =
    ctrl.vm.mode == 'play' && nextNode && nextNode.puzzle != 'fail' && ctrl.vm.canViewSolution;

  function setLoading(): void {
    clearTimeout(loading);
    loading = setTimeout(() => {
      loading = undefined;
      ctrl.redraw();
    }, 500);
  }

  return h(
    'div.puzzle__controls',
    boardControls({
      col1: [
        ...(loading
          ? [
              {
                act: 'noop',
                icon: icons.ellipsis,
                cls: { disabled: true },
              },
            ]
          : ctrl.vm.mode !== 'view'
            ? [
                {
                  act: 'solution',
                  text: i18n('viewTheSolution'),
                },
              ]
            : [
                {
                  act: 'vote-up',
                  icon: icons.thumbsUp,
                  cls: { 'vote-up': true },
                },
                {
                  act: 'vote-down',
                  icon: icons.thumbsUp,
                  cls: { 'vote-down': true },
                },
              ]),
      ],
      col2: {
        left: {
          title: i18n('practiceWithComputer'),
          act: 'practice',
          icon: icons.bullseye,
          href: `/analysis/${ctrl.vm.node.sfen.replace(/ /g, '_')}?color=${ctrl.vm.pov}#practice`,
          cls: {
            disabled: ctrl.vm.mode !== 'view',
          },
        },
        right: {
          act: 'menu',
          title: i18n('menu'),
          cls: { active: false, disabled: true },
          icon: icons.menu,
        },
      },
      onClick(act) {
        if (act === 'solution') {
          setLoading();
          ctrl.viewSolution();
        } else if (act === 'vote-up') {
          setLoading();
          ctrl.vote(true);
        } else if (act === 'vote-down') {
          setLoading();
          ctrl.vote(false);
        }
      },
      jumps: {
        first: {
          click: () => {
            control.first(ctrl);
          },
          disabled: !node.ply,
        },
        prev: {
          click: () => {
            control.prev(ctrl);
          },
          disabled: !node.ply,
        },
        next: {
          click: () => {
            control.next(ctrl);
          },
          disabled: !nextNode,
          glowing: goNext,
        },
        last: {
          click: () => {
            control.last(ctrl);
          },
          disabled: !nextNode,
          glowing: goNext,
        },
        redraw: ctrl.redraw,
      },
    }),
  );
}

let cevalShown = false;

export default function (ctrl: Controller): VNode {
  const showCeval = ctrl.vm.showComputer();
  const gaugeOn = ctrl.showEvalGauge();
  if (cevalShown !== showCeval) {
    if (!cevalShown) ctrl.vm.autoScrollNow = true;
    cevalShown = showCeval;
  }
  return h(
    `main.puzzle.puzzle-${ctrl.getData().replay ? 'replay' : 'play'}`,
    {
      class: { 'gauge-on': gaugeOn },
    },
    [
      h('aside.puzzle__side', [
        side.replay(ctrl),
        side.puzzleBox(ctrl),
        side.userBox(ctrl),
        side.config(ctrl),
        theme(ctrl),
        ctrl.curator
          ? h(
              'a',
              { attrs: { href: `/training/report/puzzle/${ctrl.getData().puzzle.id}` } },
              'View reports',
            )
          : undefined,
      ]),
      h(
        'div.puzzle__board.main-board',
        {
          hook:
            'ontouchstart' in window || window.lishogi.storage.get('scrollMoves') == '0'
              ? undefined
              : bindNonPassive(
                  'wheel',
                  stepwiseScroll((e: WheelEvent, scroll: boolean) => {
                    const target = e.target as HTMLElement;
                    if (target.tagName !== 'SG-PIECES') return;
                    e.preventDefault();
                    if (e.deltaY > 0 && scroll) control.next(ctrl);
                    else if (e.deltaY < 0 && scroll) control.prev(ctrl);
                    ctrl.redraw();
                  }),
                ),
        },
        shogiground.renderBoard(ctrl),
      ),
      cevalView.renderGauge(ctrl),
      h('div.puzzle__tools', [
        // we need the wrapping div here
        // so the siblings are only updated when ceval is added
        h(
          `div.ceval-wrap.${ctrl.vm.lastFeedback}`,
          {
            class: { active: showCeval },
          },
          showCeval ? [cevalView.renderCeval(ctrl), cevalView.renderPvs(ctrl)] : h('div.ceval'),
        ),
        renderAnalyse(ctrl),
        feedbackView(ctrl),
      ]),
      controls(ctrl),
      session(ctrl),
      ctrl.keyboardMove ? renderKeyboardMove(ctrl.keyboardMove) : null,
    ],
  );
}

function session(ctrl: Controller) {
  const rounds = ctrl.session.get().rounds;
  const current = ctrl.getData().puzzle.id;
  return h('div.puzzle__session', [
    ...rounds.map(round => {
      const rd = round.ratingDiff
        ? round.ratingDiff > 0
          ? `+${round.ratingDiff}`
          : round.ratingDiff
        : null;
      return h(
        `a.result-${round.result}${rd ? '' : '.result-empty'}`,
        {
          key: round.id,
          class: {
            current: current == round.id,
          },
          attrs: {
            href: `/training/${ctrl.session.theme}/${round.id}`,
          },
        },
        rd,
      );
    }),
    rounds.find(r => r.id == current)
      ? h('a.session-new', {
          key: 'new',
          attrs: {
            href: `/training/${ctrl.session.theme}`,
          },
        })
      : h('a.result-cursor.current', {
          key: current,
          attrs: {
            href: `/training/${ctrl.session.theme}/${current}`,
          },
        }),
  ]);
}
