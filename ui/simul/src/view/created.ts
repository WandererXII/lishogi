import { randomNumber } from 'common/common';
import { icons } from 'common/icons';
import { modal } from 'common/modal';
import { getPerfIcon } from 'common/perf-icons';
import { bind, type MaybeVNodes, proverb } from 'common/snabbdom';
import { ddloader } from 'common/spinner';
import { i18n, i18nFormat, i18nVdomPlural } from 'i18n';
import { i18nVariant } from 'i18n/variant';
import { h } from 'snabbdom';
import type SimulCtrl from '../ctrl';
import type { Applicant } from '../interfaces';
import xhr from '../xhr';
import * as util from './util';

let openModal = false;
export default function (ctrl: SimulCtrl): MaybeVNodes {
  const candidates = ctrl.candidates().sort(byName);
  const accepted = ctrl.accepted().sort(byName);
  const isHost = ctrl.createdByMe();
  const variantIconFor = (a: Applicant) => {
    const variant = ctrl.data.variants.find(v => a.player.variant == v);
    if (!variant || ctrl.data.variants.length === 1) return undefined;
    else
      return h('td.variant', {
        attrs: {
          title: i18nVariant(variant),
          'data-icon': getPerfIcon(variant),
        },
      });
  };
  return [
    h('div.box__top', [
      util.title(ctrl),
      h(
        'div.box__top__actions',
        ctrl.opts.userId
          ? isHost
            ? hostButtons(ctrl, accepted)
            : ctrl.containsMe()
              ? withdrawButton(ctrl)
              : joinButton(ctrl)
          : h(
              'a.button.text',
              {
                attrs: {
                  'data-icon': icons.play,
                  href: `/login?referrer=${window.location.pathname}`,
                },
              },
              i18n('signIn'),
            ),
      ),
    ]),
    h(
      'div.halves',
      {
        hook: {
          postpatch(_old, vnode) {
            window.lishogi.powertip.manualUserIn(vnode.elm as HTMLElement);
          },
        },
      },
      [
        h(
          'div.half.candidates',
          h('table.slist', [
            h(
              'thead',
              h(
                'tr',
                h(
                  'th',
                  {
                    attrs: { colspan: 3 },
                  },
                  i18nVdomPlural(
                    'nbCandidatePlayers',
                    candidates.length,
                    h('strong', candidates.length),
                  ),
                ),
              ),
            ),
            h(
              'tbody',
              candidates.map(applicant => {
                return h(
                  'tr',
                  {
                    key: applicant.player.id,
                    class: {
                      me: ctrl.opts.userId === applicant.player.id,
                    },
                  },
                  [
                    h('td', util.player(applicant.player, ctrl)),
                    variantIconFor(applicant),
                    h(
                      'td.action',
                      isHost
                        ? [
                            h('a.button', {
                              attrs: {
                                'data-icon': icons.right,
                                title: i18n('accept'),
                              },
                              hook: bind('click', () =>
                                xhr.accept(applicant.player.id)(ctrl.data.id),
                              ),
                            }),
                          ]
                        : [],
                    ),
                  ],
                );
              }),
            ),
          ]),
        ),
        h('div.half.accepted', [
          h('table.slist.user_list', [
            h('thead', [
              h(
                'tr',
                h(
                  'th',
                  {
                    attrs: { colspan: 3 },
                  },
                  i18nVdomPlural(
                    'nbAcceptedPlayers',
                    accepted.length,
                    h('strong', accepted.length),
                  ),
                ),
              ),
            ]),
            h(
              'tbody',
              accepted.map(applicant => {
                return h(
                  'tr',
                  {
                    key: applicant.player.id,
                    class: {
                      me: ctrl.opts.userId === applicant.player.id,
                    },
                  },
                  [
                    h('td', util.player(applicant.player, ctrl)),
                    variantIconFor(applicant),
                    h(
                      'td.action',
                      isHost
                        ? [
                            h('a.button.button-red', {
                              attrs: {
                                'data-icon': icons.cancel,
                                title: i18n('decline'),
                              },
                              hook: bind('click', () =>
                                xhr.reject(applicant.player.id)(ctrl.data.id),
                              ),
                            }),
                          ]
                        : [],
                    ),
                  ],
                );
              }),
            ),
          ]),
          isHost && accepted.length < 2 ? h('div.help', i18n('acceptPlayersStartSimul')) : null,
        ]),
      ],
    ),
    isHost && candidates.length ? randomButton(ctrl) : null,
    ctrl.data.proverb ? proverb(ctrl.data.proverb) : null,
    openModal
      ? modal({
          class: 'continue-with',
          content: ctrl.data.variants.map(variant =>
            h(
              'button.button',
              {
                hook: bind('click', () => {
                  openModal = false;
                  ctrl.loader = 'join';
                  ctrl.redraw();
                  xhr.join(ctrl, variant);
                }),
              },
              [
                h(
                  'span.text',
                  {
                    attrs: {
                      'data-icon': getPerfIcon(variant),
                    },
                  },
                  i18nVariant(variant),
                ),
              ],
            ),
          ),
          onClose() {
            openModal = false;
            ctrl.redraw();
          },
        })
      : undefined,
  ];
}

const joinButton = (ctrl: SimulCtrl) =>
  ctrl.loader === 'join'
    ? ddloader()
    : h(
        `a.button.text${ctrl.teamBlock() ? '.disabled' : ''}`,
        {
          attrs: {
            'data-icon': icons.play,
          },
          hook: ctrl.teamBlock()
            ? {}
            : bind('click', () => {
                if (ctrl.data.variants.length === 1) {
                  ctrl.loader = 'join';
                  ctrl.redraw();
                  xhr.join(ctrl, ctrl.data.variants[0]);
                } else {
                  openModal = true;
                  ctrl.redraw();
                }
              }),
        },
        ctrl.teamBlock() && ctrl.data.team
          ? i18nFormat('mustBeInTeam', ctrl.data.team.name)
          : i18n('join'),
      );

const withdrawButton = (ctrl: SimulCtrl) =>
  ctrl.loader === 'withdraw'
    ? ddloader()
    : h(
        'a.button',
        {
          hook: bind('click', () => {
            ctrl.loader = 'withdraw';
            ctrl.redraw();
            xhr.withdraw(ctrl);
          }),
        },
        i18n('withdraw'),
      );

const byName = (a: Applicant, b: Applicant) => (a.player.name > b.player.name ? 1 : -1);

const randomButton = (ctrl: SimulCtrl) =>
  h(
    'a.button.text.random-accept',
    {
      attrs: {
        'data-icon': icons.correct,
      },
      hook: bind('click', () => {
        const candidates = ctrl.candidates();
        const randomCandidate = candidates[randomNumber(candidates.length)];
        xhr.accept(randomCandidate.player.id)(ctrl.data.id);
      }),
    },
    i18n('acceptRandomCandidate'),
  );

const hostButtons = (ctrl: SimulCtrl, accepted: Applicant[]) => [
  h(
    'button.button.button-red.text',
    {
      attrs: {
        'data-icon': icons.cancel,
      },
      hook: bind('click', () => {
        if (confirm(`${i18n('delete')} - ${ctrl.data.name}`)) xhr.abort(ctrl.data.id);
      }),
    },
    i18n('cancel'),
  ),
  h(
    `button.button.button-green.text${accepted.length < 2 ? '.disabled' : ''}`,
    {
      attrs: {
        'data-icon': icons.play,
      },
      hook: bind('click', () => xhr.start(ctrl.data.id)),
    },
    i18n('start'),
  ),
];
