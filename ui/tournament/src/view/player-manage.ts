import { type MaybeVNode, bind } from 'common/snabbdom';
import { i18n } from 'i18n';
import { type VNode, type VNodes, h } from 'snabbdom';
import type TournamentController from '../ctrl';
import { backControl } from './controls';
import header from './header';

export function playerManagementView(ctrl: TournamentController): VNodes {
  return [
    header(ctrl),
    backControl(() => {
      ctrl.playerManagement = false;
      ctrl.redraw();
    }),
    playerManagement(ctrl),
  ];
}

let toKick = '';
function playerManagement(ctrl: TournamentController): VNode {
  return h('div.player-manage', [
    ctrl.data.candidatesOnly ? renderCandidates(ctrl) : null,
    renderDenied(ctrl),
    h('div.other-actions', [
      h('div.ban', [
        h(
          'div.search',
          h('input', {
            hook: {
              insert(vnode) {
                requestAnimationFrame(() => {
                  const el = vnode.elm as HTMLInputElement;
                  window.lishogi.userAutocomplete($(el), {
                    tag: 'span',
                    tour: ctrl.data.id,
                    focus: false,
                    minLength: 3,
                    onSelect(v: any) {
                      toKick = v.id;
                      ctrl.redraw();
                    },
                  });
                });
              },
            },
          }),
        ),
        h(
          'button.button.button-red',
          {
            class: {
              disabled: !toKick,
            },
            hook: bind('click', () => {
              if (confirm(i18n('notReversible'))) {
                ctrl.playerKick(toKick);
                toKick = '';
                ctrl.redraw();
              }
            }),
          },
          i18n('kickPlayer'),
        ),
      ]),
      h('div.joining', [
        h(
          'button.button.button-red',
          {
            hook: bind('click', () => {
              ctrl.closeJoining(!ctrl.data.isClosed);
              ctrl.redraw();
            }),
          },
          ctrl.data.isClosed ? 'Open joining' : 'Close joining',
        ),
      ]),
    ]),
  ]);
}

function renderCandidates(ctrl: TournamentController): MaybeVNode {
  return h('div.candidates', [
    h('h3.title', 'Tournament candidates'),
    h(
      'div.table-wrap',
      h(
        'table.slist.slist-pad',
        h(
          'tbody',
          (ctrl.data.candidates || []).map(c => {
            return h(
              'tr',
              {
                class: {
                  shaded: ctrl.shadedCandidates.includes(c.id),
                },
              },
              [
                h('td.name', renderUser(c)),
                h('td.actions', [
                  h(
                    'button.button.text',
                    {
                      attrs: { 'data-icon': 'E', title: i18n('accept') },
                      hook: bind('click', () => ctrl.processCandidate(c.id, true)),
                    },
                    i18n('accept'),
                  ),
                  h('button.button.button-red', {
                    attrs: { 'data-icon': 'L', title: i18n('decline') },
                    hook: bind('click', () => ctrl.processCandidate(c.id, false)),
                  }),
                ]),
              ],
            );
          }),
        ),
      ),
    ),
  ]);
}

function renderDenied(ctrl: TournamentController) {
  return h('div.denied', [
    h('h3.title', 'Kicked and denied players'),
    h(
      'div.table-wrap',
      h(
        'table.slist.slist-pad',
        h(
          'tbody',
          (ctrl.data.denied || []).map(d => {
            return h(
              'tr',
              {
                class: {
                  shaded: ctrl.shadedCandidates.includes(d.id),
                },
              },
              [
                h('td.name', renderUser(d)),
                h('td.actions', [
                  h('button.button', {
                    attrs: { 'data-icon': 'P', title: i18n('accept') },
                    hook: bind('click', () => ctrl.processCandidate(d.id, true)),
                  }),
                ]),
              ],
            );
          }),
        ),
      ),
    ),
  ]);
}

function renderUser(user: LightUser) {
  return h(
    'a.ulpt.user-link',
    {
      attrs: { href: `/@/${user.name}` },
      hook: {
        destroy: vnode => $.powerTip.destroy(vnode.elm as HTMLElement),
      },
    },
    user.title ? [h('span.title', user.title), ` ${user.name}`] : user.name,
  );
}
