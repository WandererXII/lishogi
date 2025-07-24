import { modal } from 'common/modal';
import { type MaybeVNode, dataIcon } from 'common/snabbdom';
import spinner from 'common/spinner';
import { i18n } from 'i18n';
import { type VNode, h } from 'snabbdom';
import type TournamentController from '../../ctrl';
import { numberRow, player as renderPlayer } from '../util';
import { teamName } from './battle';

export function teamInfoModal(ctrl: TournamentController): MaybeVNode {
  return ctrl.teamInfo.requested
    ? modal({
        class: 'actor-info-modal',
        content: [teamInfo(ctrl)],
        onClose: () => ctrl.unshowTeamInfo(),
      })
    : undefined;
}

function teamInfo(ctrl: TournamentController): VNode | undefined {
  const battle = ctrl.data.teamBattle;
  const data = ctrl.teamInfo.loaded;
  if (!battle) return undefined;
  const teamTag = ctrl.teamInfo.requested ? teamName(battle, ctrl.teamInfo.requested) : null;
  const tag = 'div.tour__team-info.tour__actor-info';
  if (!data || data.id !== ctrl.teamInfo.requested)
    return h(tag, [h('div.stats', [h('h2', [teamTag]), spinner()])]);
  const nbLeaders = ctrl.data.teamStanding?.find(s => s.id == data.id)?.players.length || 0;

  const setup = (vnode: VNode) => {
    window.lishogi.powertip.manualUserIn(vnode.elm as HTMLElement);
  };
  return h(
    tag,
    {
      hook: {
        insert: setup,
        postpatch(_, vnode) {
          setup(vnode);
        },
      },
    },
    [
      h('div.stats', [
        h('h2', [teamTag]),
        h('table', [
          numberRow('Players', data.nbPlayers),
          ...(data.rating
            ? [
                numberRow(i18n('averageElo'), data.rating, 'raw'),
                ...(data.perf
                  ? [
                      numberRow(i18n('arena:averagePerformance'), data.perf, 'raw'),
                      numberRow(i18n('arena:averageScore'), data.score, 'raw'),
                    ]
                  : []),
              ]
            : []),
          h(
            'tr',
            h(
              'th',
              h(
                'a',
                {
                  attrs: { href: `/team/${data.id}` },
                },
                i18n('team:teamPage'),
              ),
            ),
          ),
        ]),
      ]),
      h('div', [
        h(
          'table.sublist',
          data.topPlayers.map((p, i) =>
            h(
              'tr',
              {
                key: p.name,
                on: {
                  click: () => {
                    ctrl.jumpToPageOf(p.name);
                  },
                },
              },
              [
                h('th', `${i + 1}`),
                h(
                  'td',
                  renderPlayer(p, {
                    asLink: false,
                    withRating: true,
                    leader: i < nbLeaders,
                  }),
                ),
                h('td.total', [
                  p.fire && !ctrl.data.isFinished
                    ? h('strong.is-gold', { attrs: dataIcon('Q') }, `${p.score}`)
                    : h('strong', `${p.score}`),
                ]),
              ],
            ),
          ),
        ),
      ]),
    ],
  );
}
