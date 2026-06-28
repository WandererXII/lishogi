import * as cevalView from 'ceval/view';
import { chatGameMembers, chatMembers, makeChat } from 'chat';
import { boardControls } from 'common/board-controls';
import { defined } from 'common/common';
import { icons } from 'common/icons';
import { hasTouchEvents } from 'common/mobile';
import { getPerfIcon } from 'common/perf-icons';
import { prefs } from 'common/prefs';
import { bind, bindNonPassive, dataIcon, type MaybeVNode, onInsert } from 'common/snabbdom';
import spinner from 'common/spinner';
import stepwiseScroll from 'common/wheel';
import { playable } from 'game/game';
import * as router from 'game/router';
import { finished } from 'game/status';
import { studyModal } from 'game/view/post-game-study';
import statusView from 'game/view/status';
import { i18n, i18nFormatCapitalized, i18nPluralSame } from 'i18n';
import { i18nVariant } from 'i18n/variant';
import { colorName } from 'shogi/color-name';
import { parseSfen } from 'shogiops/sfen';
import { h, type VNode } from 'snabbdom';
import { path as treePath } from 'tree';
import { render as acplView } from './acpl';
import { view as actionMenu, continueWithModal } from './action-menu';
import * as control from './control';
import type AnalyseCtrl from './ctrl';
import forecastView from './forecast/forecast-view';
import { view as forkView } from './fork';
import * as shogiground from './ground';
import type { ConcealOf } from './interfaces';
import { view as keyboardView } from './keyboard';
import * as notationExport from './notation-export';
import { renderPlayerBars } from './player-bars';
import practiceView from './practice/practice-view';
import retroView from './retrospect/retro-view';
import serverSideUnderboard from './server-side-underboard';
import * as gbEdit from './study/gamebook/gamebook-edit';
import * as gbPlay from './study/gamebook/gamebook-play-view';
import type { StudyCtrl } from './study/interfaces';
import * as studyView from './study/study-view';
import { render as renderTreeView } from './tree-view/tree-view';

function renderResult(ctrl: AnalyseCtrl): MaybeVNode {
  const handicap = ctrl.isHandicap();
  const render = (status: string, winner?: Color) =>
    h('div.status', [
      status,
      winner ? `, ${i18nFormatCapitalized('xIsVictorious', colorName(winner, handicap))}` : null,
    ]);
  if (finished(ctrl.data)) {
    const status = statusView(
      ctrl.data.game.variant.key,
      ctrl.data.game.status,
      ctrl.data.game.winner,
      handicap,
    );
    return render(status, ctrl.data.game.winner);
  } else if (ctrl.study?.data.chapter.setup.endStatus) {
    const status = statusView(
      ctrl.study.data.chapter.setup.variant.key,
      ctrl.study.data.chapter.setup.endStatus.status,
      ctrl.study.data.chapter.setup.endStatus.winner,
      handicap,
    );
    return render(status, ctrl.study.data.chapter.setup.endStatus.winner);
  } else return null;
}

function makeConcealOf(ctrl: AnalyseCtrl): ConcealOf | undefined {
  const conceal =
    ctrl.study && ctrl.study.data.chapter.conceal !== undefined
      ? {
          owner: ctrl.study.isChapterOwner(),
          ply: ctrl.study.data.chapter.conceal,
        }
      : null;
  if (conceal)
    return (isMainline: boolean) => (path: Tree.Path, node: Tree.Node) => {
      if (!conceal || (isMainline && conceal.ply >= node.ply)) return null;
      if (treePath.contains(ctrl.path, path)) return null;
      return conceal.owner ? 'conceal' : 'hide';
    };
  return undefined;
}

function renderAnalyseMoves(ctrl: AnalyseCtrl, concealOf?: ConcealOf) {
  return h('div.analyse__moves.areplay', [
    ctrl.embed && ctrl.study ? h('div.chapter-name', ctrl.study.currentChapter().name) : null,
    renderTreeView(ctrl, concealOf),
    renderResult(ctrl),
  ]);
}

function inputs(ctrl: AnalyseCtrl): VNode | undefined {
  if (ctrl.ongoing || !ctrl.data.userAnalysis) return;
  if (ctrl.redirecting) return spinner();
  return h('div.copyables', [
    h('div.pair', [
      h('label.name', 'SFEN'),
      h('input.copyable.autoselect.analyse__underboard__sfen', {
        attrs: { spellCheck: false },
        hook: {
          insert: vnode => {
            const el = vnode.elm as HTMLInputElement;
            el.value = defined(ctrl.sfenInput) ? ctrl.sfenInput : ctrl.node.sfen;
            el.addEventListener('change', _ => {
              if (el.value !== ctrl.node.sfen && el.reportValidity())
                ctrl.changeSfen(el.value.trim());
            });
            el.addEventListener('input', _ => {
              ctrl.sfenInput = el.value;
              el.addEventListener('input', _ => {
                ctrl.sfenInput = el.value;
                const position = parseSfen(ctrl.data.game.variant.key, el.value.trim(), false);
                el.setCustomValidity(position.isOk ? '' : 'Invalid SFEN');
              });
            });
          },
          postpatch: (_, vnode) => {
            const el = vnode.elm as HTMLInputElement;
            if (!defined(ctrl.sfenInput)) {
              el.value = ctrl.node.sfen;
              el.setCustomValidity('');
            } else if (el.value != ctrl.sfenInput) {
              el.value = ctrl.sfenInput;
            }
          },
        },
      }),
    ]),
    h('div.kif', [
      h('div.pair', [
        h('label.name', 'KIF'),
        h('textarea.copyable.autoselect', {
          attrs: { spellCheck: false },
          hook: {
            ...onInsert(el => {
              (el as HTMLTextAreaElement).value = defined(ctrl.kifInput)
                ? ctrl.kifInput
                : notationExport.renderFullKif(ctrl);
              el.addEventListener('input', e => {
                ctrl.kifInput = (e.target as HTMLTextAreaElement).value;
              });
            }),
            postpatch: (_, vnode) => {
              (vnode.elm as HTMLTextAreaElement).value = defined(ctrl.kifInput)
                ? ctrl.kifInput
                : notationExport.renderFullKif(ctrl);
            },
          },
        }),
        h(
          'button.button.button-thin.action.text',
          {
            attrs: dataIcon(icons.play),
            hook: bind(
              'click',
              _ => {
                const kif = $('.copyables .kif textarea').val() as string;
                if (kif !== notationExport.renderFullKif(ctrl)) ctrl.changeNotation(kif);
              },
              ctrl.redraw,
            ),
          },
          i18n('importKif'),
        ),
      ]),
    ]),
    ['standard'].includes(ctrl.data.game.variant.key)
      ? h('div.csa', [
          h('div.pair', [
            h('label.name', 'CSA'),
            h('textarea.copyable.autoselect', {
              attrs: { spellCheck: false },
              hook: {
                ...onInsert(el => {
                  (el as HTMLTextAreaElement).value = defined(ctrl.csaInput)
                    ? ctrl.csaInput
                    : notationExport.renderFullCsa(ctrl);
                  el.addEventListener('input', e => {
                    ctrl.csaInput = (e.target as HTMLTextAreaElement).value;
                  });
                }),
                postpatch: (_, vnode) => {
                  (vnode.elm as HTMLTextAreaElement).value = defined(ctrl.csaInput)
                    ? ctrl.csaInput
                    : notationExport.renderFullCsa(ctrl);
                },
              },
            }),
            h(
              'button.button.button-thin.action.text',
              {
                attrs: dataIcon(icons.play),
                hook: bind(
                  'click',
                  _ => {
                    const csa = $('.copyables .csa textarea').val() as string;
                    if (csa !== notationExport.renderFullCsa(ctrl)) ctrl.changeNotation(csa);
                  },
                  ctrl.redraw,
                ),
              },
              i18n('importCsa'),
            ),
          ]),
        ])
      : null,
    h('div.url', [
      h('div.pair', [
        h('label.name', 'URL'),
        h('input.copyable.autoselect', {
          attrs: { spellCheck: false },
          hook: {
            ...onInsert(el => {
              (el as HTMLTextAreaElement).value = defined(ctrl.urlInput)
                ? ctrl.urlInput
                : notationExport.renderUrlUsiLine(ctrl);
              el.addEventListener('input', e => {
                ctrl.urlInput = (e.target as HTMLTextAreaElement).value;
              });
            }),
            postpatch: (_, vnode) => {
              (vnode.elm as HTMLTextAreaElement).value = defined(ctrl.urlInput)
                ? ctrl.urlInput
                : notationExport.renderUrlUsiLine(ctrl);
            },
          },
        }),
      ]),
      h('div.pair', [
        h('div.name'), // just for proper spacing
        h(
          'div.form-help.text',
          {
            attrs: { 'data-icon': icons.infoCircle },
          },
          [
            i18n('shareMainlineUrl'),
            ctrl.mainline.length > 300
              ? h('span.error', `MAX: ${i18nPluralSame('nbMoves', 300)}`)
              : null,
          ],
        ),
      ]),
    ]),
  ]);
}

function controls(ctrl: AnalyseCtrl) {
  return h('div.analyse__controls', [allControls(ctrl)]);
}

function allControls(ctrl: AnalyseCtrl) {
  const menuIsOpen = ctrl.actionMenu.open;
  const canJumpPrev = ctrl.path !== '';
  const canJumpNext = !!ctrl.node.children[0];

  return boardControls({
    col1:
      ctrl.opts.mode === 'analyse'
        ? [
            {
              act: 'variant-selector',
              icon: getPerfIcon(ctrl.data.game.variant.key),
              text: i18nVariant(ctrl.data.game.variant.key),
              cls: { 'variant-selector': true, text: true },
            },
            {
              act: 'practice',
              icon: icons.bullseye,
            },
            {
              act: 'menu',
              icon: icons.menu,
              cls: {
                active: menuIsOpen,
              },
            },
          ]
        : [
            {
              text: 'Shared',
              icon: icons.people,
              act: 'shared',
            },
            ctrl.opts.mode === 'replay'
              ? {
                  icon: icons.challenge,
                  act: 'rematch',
                }
              : undefined,
            {
              icon: icons.talk,
              act: 'chat',
            },
            ctrl.study
              ? {
                  icon: ctrl.study.data.liked ? icons.heartFull : icons.heartOutline,
                  act: 'like',
                }
              : undefined,
            ctrl.study
              ? {
                  icon: icons.gear,
                  act: 'settings',
                }
              : undefined,
            {
              act: 'menu',
              icon: icons.menu,
              cls: {
                active: menuIsOpen,
              },
            },
          ],
    col2: {
      left:
        ctrl.embed || ctrl.forecast
          ? undefined
          : {
              title: i18n('practiceWithComputer'),
              act: 'practice',
              icon: icons.bullseye,
              cls: {
                active: !!ctrl.practice,
                disabled:
                  menuIsOpen ||
                  !(ctrl.ceval.possible && ctrl.ceval.allowed() && !ctrl.isGamebook()),
              },
            },
      right: {
        act: 'menu',
        title: i18n('menu'),
        cls: { active: menuIsOpen },
        icon: icons.menu,
      },
    },
    onClick(act) {
      if (act === 'menu') {
        ctrl.actionMenu.toggle();
        ctrl.redraw();
      } else if (act === 'practice') {
        ctrl.togglePractice();
      } else if (act === 'variant-selector') {
        document.querySelector<HTMLElement>('.mselect__label')?.click();
      }
    },
    jumps: {
      first: {
        click: () => {
          control.first(ctrl);
        },
        disabled: !canJumpPrev,
      },
      prev: {
        click: () => {
          control.prev(ctrl);
        },
        disabled: !canJumpPrev,
      },
      next: {
        click: () => {
          control.next(ctrl);
        },
        disabled: !canJumpNext,
      },
      last: {
        click: () => {
          control.last(ctrl);
        },
        disabled: !canJumpNext,
      },
      redraw: ctrl.redraw,
    },
  });
}

function forceInnerCoords(ctrl: AnalyseCtrl, v: boolean) {
  if (ctrl.data.pref.coords == prefs.coords.OUTSIDE) {
    $('body').toggleClass('coords-in', v).toggleClass('coords-out', !v);
  }
}

function addChapterId(study: StudyCtrl | undefined, cssClass: string) {
  return cssClass + (study?.data.chapter ? `.${study.data.chapter.id}` : '');
}

export default function (ctrl: AnalyseCtrl): VNode {
  if (ctrl.nvui) return ctrl.nvui.render(ctrl);
  const concealOf = makeConcealOf(ctrl);
  const study = ctrl.study;
  const showCevalPvs = !ctrl.retro?.isSolving() && !ctrl.practice;
  const menuIsOpen = ctrl.actionMenu.open;
  const gamebookPlay = ctrl.gamebookPlay();
  const gamebookPlayView = gamebookPlay && gbPlay.render(gamebookPlay);
  const gamebookEditView = gbEdit.running(ctrl) ? gbEdit.render(ctrl) : undefined;
  const playerBars = renderPlayerBars(ctrl);
  const gaugeOn = ctrl.showEvalGauge();
  const needsInnerCoords = !!playerBars;

  return h(
    `main.sb-insert.analyse.main-v-${ctrl.data.game.variant.key}.mode-${ctrl.opts.mode}`, // sb-insert - to force snabbdom to call insert
    {
      hook: {
        insert: () => {
          forceInnerCoords(ctrl, needsInnerCoords);
          if (!!playerBars != $('body').hasClass('header-margin')) {
            requestAnimationFrame(() => {
              $('body').toggleClass('header-margin', !!playerBars);
              ctrl.redraw();
            });
          }
        },
        update(_, _2) {
          forceInnerCoords(ctrl, needsInnerCoords);
        },
        postpatch(old, vnode) {
          if (old.data!.gaugeOn !== gaugeOn)
            document.body.dispatchEvent(new Event('shogiground.resize'));
          vnode.data!.gaugeOn = gaugeOn;
        },
      },
      class: {
        'comp-off': !ctrl.showComputer(),
        'gauge-on': gaugeOn,
        'has-player-bars': !!playerBars,
        'post-game': !!ctrl.study?.data.postGameStudy,
      },
    },
    [
      ctrl.keyboardHelp ? keyboardView(ctrl) : null,
      ctrl.studyModal()
        ? studyModal(ctrl.data.game.id, ctrl.shogiground.state.orientation, () => {
            ctrl.studyModal(false);
            ctrl.redraw();
          })
        : null,
      study ? studyView.overboard(study) : null,
      ctrl.continueWith ? continueWithModal(ctrl) : null,
      h(
        'div.analyse__board',
        h(
          addChapterId(
            study,
            `div.analyse__board-inner.main-board.v-${ctrl.data.game.variant.key}`,
          ),
          {
            hook:
              hasTouchEvents ||
              ctrl.gamebookPlay() ||
              window.lishogi.storage.get('scrollMoves') == '0'
                ? undefined
                : bindNonPassive(
                    'wheel',
                    stepwiseScroll((e: WheelEvent, scroll: boolean) => {
                      if (ctrl.gamebookPlay()) return;
                      const target = e.target as HTMLElement;
                      if (target.tagName !== 'SG-PIECES') return;
                      e.preventDefault();
                      if (e.deltaY > 0 && scroll) control.next(ctrl);
                      else if (e.deltaY < 0 && scroll) control.prev(ctrl);
                      ctrl.redraw();
                    }),
                  ),
          },
          [
            playerBars ? playerBars[ctrl.bottomIsSente() ? 1 : 0] : null,
            shogiground.renderBoard(ctrl),
            playerBars ? playerBars[ctrl.bottomIsSente() ? 0 : 1] : null,
          ],
        ),
      ),
      gaugeOn ? cevalView.renderGauge(ctrl) : null,
      gamebookPlayView ||
        h(addChapterId(study, 'div.analyse__tools'), [
          ...(menuIsOpen
            ? [actionMenu(ctrl)]
            : [
                cevalView.renderCeval(ctrl) || h('div.ceval'),
                showCevalPvs ? cevalView.renderPvs(ctrl) : null,
                renderAnalyseMoves(ctrl, concealOf),
                gamebookEditView || forkView(ctrl, concealOf),
                retroView(ctrl) || practiceView(ctrl),
              ]),
        ]),
      gamebookPlayView ? null : controls(ctrl),
      ctrl.embed
        ? null
        : h(
            'div.analyse__underboard',
            {
              hook:
                ctrl.synthetic || playable(ctrl.data)
                  ? undefined
                  : onInsert(elm => serverSideUnderboard(elm, ctrl)),
            },
            study ? studyView.underboard(ctrl) : [inputs(ctrl)],
          ),
      acplView(ctrl),
      ctrl.embed
        ? null
        : h(
            'aside.analyse__side',
            {
              hook: onInsert(elm => {
                ctrl.opts.$side?.length && $(elm).replaceWith(ctrl.opts.$side);
              }),
            },
            (study
              ? [studyView.side(study)]
              : [
                  ctrl.forecast ? forecastView(ctrl, ctrl.forecast) : null,
                  !ctrl.synthetic && playable(ctrl.data)
                    ? h(
                        'div.back-to-game',
                        h(
                          'a.button.button-empty.text',
                          {
                            attrs: {
                              href: router.game(ctrl.data, ctrl.data.player.color),
                              'data-icon': icons.back,
                            },
                          },
                          i18n('backToGame'),
                        ),
                      )
                    : null,
                ]
            ).concat(
              ctrl.opts.$meta
                ? h('div.meta', {
                    hook: onInsert(el => {
                      $(el).replaceWith(ctrl.opts.$meta!);

                      const rematchButton = document.querySelector('.game__links .button.rematch');
                      rematchButton?.addEventListener('click', () => {
                        ctrl.socket.send('rematch-yes');
                        rematchButton.classList.toggle('sent');
                      });
                    }),
                  })
                : null,
              ctrl.opts.$streamers
                ? h('div.context-streamers', {
                    hook: onInsert(el => {
                      $(el).replaceWith(ctrl.opts.$streamers!.removeClass('none'));
                    }),
                  })
                : null,
              ctrl.opts.chat
                ? h('section.mchat', {
                    hook: onInsert(_ => {
                      if (ctrl.opts.chat.instance) ctrl.opts.chat.instance.destroy();
                      ctrl.opts.chat.parseMoves = true;
                      ctrl.opts.chat.playerFilter = !finished(ctrl.data);

                      const data = ctrl.opts.data;
                      const sentePlayer =
                        data.player.color === 'sente' ? data.player : data.opponent;
                      const gotePlayer = data.player.color === 'gote' ? data.player : data.opponent;
                      ctrl.opts.chat.players = {
                        sente: sentePlayer.user?.id,
                        gote: gotePlayer.user?.id,
                      };
                      ctrl.opts.chat.instance = makeChat(ctrl.opts.chat);
                    }),
                  })
                : null,
            ),
          ),
      ctrl.embed ? null : ctrl.opts.mode === 'replay' ? chatGameMembers() : chatMembers(),
    ],
  );
}
