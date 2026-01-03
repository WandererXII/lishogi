import { defined } from 'common/common';
import * as game from 'game';
import { colorName } from 'shogi/color-name';
import { engineNameFromCode } from 'shogi/engine-name';
import { rankFromRating } from 'shogi/rank';
import { type UsernameData, usernameDataFromName, usernameVNodes } from 'shogi/username';
import { COLORS } from 'shogiops/constants';
import { h, type VNode } from 'snabbdom';
import { ops as treeOps } from 'tree';
import { renderClockOf } from './clocks';
import type AnalyseCtrl from './ctrl';
import { findTag } from './study/study-chapters';

export function renderPlayerBars(ctrl: AnalyseCtrl): VNode[] | undefined {
  if (ctrl.embed || ctrl.forecast) return;

  const sente = findPlayer(ctrl, 'sente');
  const gote = findPlayer(ctrl, 'gote');

  const noNames = ['', '?', undefined];
  if (
    noNames.includes(sente.username) &&
    noNames.includes(gote.username) &&
    !treeOps.findInMainline(ctrl.tree.root, n => {
      return defined(n.clock) && !!n.id;
    })
  )
    return;

  return COLORS.map(color =>
    renderPlayer(ctrl, color === 'sente' ? sente : gote, color, ctrl.bottomColor() !== color),
  );
}

function renderPlayer(
  ctrl: AnalyseCtrl,
  usernameData: UsernameData,
  color: Color,
  top: boolean,
): VNode {
  return h(`div.player-bar.player-bar-${top ? 'top' : 'bot'}`, [
    h(
      'div.name-wrap',
      h(
        `span.name.color-icon.${color}.is.text.user-link`,
        {
          attrs: {
            title: colorName(color, ctrl.isHandicap()),
          },
        },
        usernameVNodes(usernameData),
      ),
    ),
    h(
      'div.clock-wrap',
      ctrl.onMainline || ctrl.node.clock ? renderClockOf(ctrl, color) : undefined,
    ),
  ]);
}

function findPlayer(ctrl: AnalyseCtrl, color: Color): UsernameData {
  const study = ctrl.study;

  if (study) {
    const name = findTag(study.data.chapter.tags, color);
    return usernameDataFromName(name);
  } else {
    const player = game.getPlayer(ctrl.data, color);
    const rank = !player.provisional && player.rating ? rankFromRating(player.rating) : undefined;
    if (player.user)
      return {
        username: player.user.username,
        rank: rank,
        title: player.user.title,
      };
    else if (player.ai)
      return {
        username: engineNameFromCode(player.aiCode),
        engineLvl: player.ai,
      };
    else if (player.name || !ctrl.synthetic) return usernameDataFromName(player.name);
    else
      return {
        username: '?',
      };
  }
}
