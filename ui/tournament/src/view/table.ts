import { icons } from 'common/icons';
import { bind } from 'common/snabbdom';
import { opposite } from 'shogiground/util';
import { h, type VNode } from 'snabbdom';
import type TournamentController from '../ctrl';
import type {
  Duel,
  DuelPlayer,
  DuelTeams,
  Featured,
  FeaturedPlayer,
  TeamBattle,
} from '../interfaces';
import { teamName } from './arena/battle';
import { miniBoard, player as renderPlayer } from './util';

function featuredPlayer(player: FeaturedPlayer) {
  return h('div.tour__featured__player', [
    h('strong', `#${player.rank}`),
    renderPlayer(player, {
      asLink: true,
      withRating: true,
    }),
    player.berserk
      ? h('i', {
          attrs: {
            'data-icon': icons.berserk,
            title: 'Berserk',
          },
        })
      : null,
  ]);
}

function featured(f: Featured): VNode {
  return h('div.tour__featured', [
    featuredPlayer(f[opposite(f.color)]),
    miniBoard(f),
    featuredPlayer(f[f.color]),
  ]);
}

function duelPlayerMeta(p: DuelPlayer) {
  return [h('em.rank', `#${p.k}`), p.t ? h('em.title', p.t) : null, h('em.rating', `${p.r}`)];
}

function renderDuel(battle?: TeamBattle, duelTeams?: DuelTeams) {
  return (d: Duel) =>
    h(
      'a.glpt',
      {
        key: d.id,
        attrs: { href: `/${d.id}` },
      },
      [
        battle && duelTeams
          ? h(
              'line.t',
              [0, 1].map(i => teamName(battle, duelTeams[d.p[i].n.toLowerCase()])),
            )
          : undefined,
        h('line.a', [h('strong', d.p[0].n), h('span', duelPlayerMeta(d.p[1]).reverse())]),
        h('line.b', [h('span', duelPlayerMeta(d.p[0])), h('strong', d.p[1].n)]),
      ],
    );
}

export default function (ctrl: TournamentController): VNode | undefined {
  return ctrl.isRobin() || ctrl.isOrganized()
    ? undefined
    : h('div.tour__table', [
        ctrl.data.featured ? featured(ctrl.data.featured) : null,
        ctrl.data.duels.length
          ? h(
              'section.tour__duels',
              {
                hook: bind('click', _ => !ctrl.disableClicks),
              },
              [h('h2', 'Top games')].concat(
                ctrl.data.duels.map(renderDuel(ctrl.data.teamBattle, ctrl.data.duelTeams)),
              ),
            )
          : null,
      ]);
}
