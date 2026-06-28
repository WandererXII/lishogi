import { icons } from 'common/icons';
import { modal } from 'common/modal';
import { bind, dataIcon, onInsert } from 'common/snabbdom';
import { i18n, i18nFormat } from 'i18n';
import { h, type VNode } from 'snabbdom';
import type { Controller } from '../interfaces';

const renderVote = (ctrl: Controller): VNode =>
  h(
    'div.puzzle__vote',
    ctrl.autoNexting()
      ? []
      : [
          ctrl.session.isNew() && ctrl.getData().user?.provisional
            ? h('div.puzzle__vote__help', [
                h('p', i18n('puzzle:didYouLikeThisPuzzle')),
                h('p', i18n('puzzle:voteToLoadNextOne')),
              ])
            : null,
          h(
            'div.puzzle__vote__buttons',
            {
              class: {
                enabled: !ctrl.vm.voteDisabled,
              },
            },
            [
              h('div.vote.vote-up', {
                hook: bind('click', () => ctrl.vote(true)),
              }),
              h('div.vote.vote-down', {
                hook: bind('click', () => ctrl.vote(false)),
              }),
            ],
          ),
        ],
  );

const renderContinue = (ctrl: Controller) =>
  h(
    'a.continue',
    {
      hook: bind('click', ctrl.nextPuzzle),
    },
    [h('i', { attrs: dataIcon(icons.play) }), i18n('puzzle:continueTraining')],
  );

let showReport = false;
export default function (ctrl: Controller): VNode {
  const data = ctrl.getData();
  return h(`div.puzzle__feedback.after.${ctrl.vm.result}`, [
    h(
      'div.complete',
      ctrl.vm.result == 'win' ? i18n('puzzle:puzzleSuccess') : i18n('puzzle:puzzleComplete'),
    ),
    data.user ? renderVote(ctrl) : renderContinue(ctrl),
    h('div.puzzle__more', [
      h('div', [
        ctrl.getData().user
          ? h('a.puzzle-report', {
              hook: bind('click', () => {
                showReport = true;
                ctrl.redraw();
              }),
              attrs: {
                'data-icon': icons.warning,
                title: i18n('reportPuzzle'),
              },
            })
          : undefined,
        showReport
          ? modal({
              class: 'puzzle-report',
              content: [
                h('div.title', i18nFormat('puzzle:puzzleId', `#${data.puzzle.id}`)),
                ctrl.vm.reported
                  ? h('div', i18n('thanksForReport'))
                  : h(
                      'form',
                      {
                        hook: onInsert((el: HTMLFormElement) => {
                          el.addEventListener('submit', (e: any) => {
                            e.preventDefault();
                            window.lishogi.xhr
                              .formToXhr(el)
                              .then(res => res.json())
                              .then(() => {
                                ctrl.vm.reported = true;
                                ctrl.redraw();
                              })
                              .catch(error => {
                                try {
                                  const res = error as Response;
                                  alert(`${res.statusText} - ${res.status}`);
                                } catch {
                                  console.error(error);
                                }
                              });
                          });
                        }),
                        attrs: { action: '/training/report/create', method: 'post' },
                      },
                      [
                        h('input', {
                          attrs: {
                            type: 'hidden',
                            name: 'puzzleId',
                            value: data.puzzle.id,
                            readonly: true,
                          },
                        }),
                        h('label', { attrs: { for: 'report-text' } }, i18n('reportPuzzleReason')),
                        h('textarea', {
                          attrs: {
                            id: 'report-text',
                            name: 'text',
                            rows: 3,
                            cols: 40,
                          },
                        }),
                        h('button.button', { attrs: { type: 'submit' } }, i18n('reportPuzzle')),
                      ],
                    ),
              ],
              onClose: () => {
                showReport = false;
                ctrl.redraw();
              },
            })
          : undefined,
        ctrl.curator
          ? h('a', {
              hook: bind('click', () => {
                if (confirm(`Are you sure you want to delete this puzzle - ${data.puzzle.id}`)) {
                  window.lishogi.xhr
                    .text('POST', `/training/delete/${data.puzzle.id}`)
                    .then(() => window.lishogi.redirect('/training'));
                }
              }),
              attrs: {
                'data-icon': icons.trashBin,
                title: i18n('delete'),
              },
            })
          : undefined,
      ]),
      ctrl.getData().user
        ? h(
            'a',
            {
              hook: bind('click', ctrl.nextPuzzle),
            },
            i18n('puzzle:continueTraining'),
          )
        : undefined,
    ]),
  ]);
}
