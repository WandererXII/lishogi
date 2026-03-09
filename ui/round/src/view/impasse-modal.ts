import { icons } from 'common/icons';
import { modal } from 'common/modal';
import type { MaybeVNode } from 'common/snabbdom';
import type { Player } from 'game/interfaces';
import { i18n } from 'i18n';
import { engineNameFromCode } from 'shogi/engine-name';
import {
  impasseInfo,
  impasseNecessaryEnteredPieces,
  impasseNecessaryGoteScore,
  impasseNecessarySenteScore,
} from 'shogi/impasse';
import { h } from 'snabbdom';
import type RoundController from '../ctrl';

export function impasseModal(ctrl: RoundController): MaybeVNode {
  const lastStep = ctrl.data.steps[ctrl.data.steps.length - 1];
  const rules = ctrl.data.game.variant.key;
  const initialSfen = ctrl.data.game.initialSfen;
  const i = impasseInfo(rules, lastStep.sfen, initialSfen);

  const sentePlayer = ctrl.data.player.color === 'sente' ? ctrl.data.player : ctrl.data.opponent;
  const gotePlayer = ctrl.data.player.color === 'gote' ? ctrl.data.player : ctrl.data.opponent;

  if (!i) return null;

  return modal({
    class: 'round__modal.impasse-help',
    onClose() {
      ctrl.impasseHelp = false;
      ctrl.redraw();
    },
    content: [
      h('div.impasse-wrap', [
        h(
          'h4',
          h(
            'a.text',
            { attrs: { 'data-icon': icons.infoCircle, href: '/page/impasse', target: '_blank' } },
            i18n('impasse'),
          ),
        ),
        h('div.impasse', [
          h('div.color-icon.sente', [
            h('span', username(sentePlayer)),
            h('ul.impasse-list', [
              h('li', [
                `${i18n('enteringKing')}: `,
                h(`span${i.sente.king ? '.good' : ''}`, {
                  attrs: { 'data-icon': i.sente.king ? icons.correct : icons.cancel },
                }),
              ]),
              h('li', [
                `${i18n('invadingPieces')}: `,
                h(
                  `span${i.sente.nbOfPieces >= impasseNecessaryEnteredPieces ? '.good' : ''}`,
                  `${i.sente.nbOfPieces}/${impasseNecessaryEnteredPieces}`,
                ),
              ]),
              h('li', [
                `${i18n('totalImpasseValue')}: `,
                h(
                  `span${i.sente.pieceValue >= impasseNecessarySenteScore ? '.good' : ''}`,
                  `${i.sente.pieceValue}/${impasseNecessarySenteScore}`,
                ),
              ]),
            ]),
          ]),
          h('div.color-icon.gote', [
            h('span', username(gotePlayer)),
            h('ul.impasse-list', [
              h('li', [
                `${i18n('enteringKing')}: `,
                h(`span${i.gote.king ? '.good' : ''}`, {
                  attrs: { 'data-icon': i.gote.king ? icons.correct : icons.cancel },
                }),
              ]),
              h('li', [
                `${i18n('invadingPieces')}: `,
                h(
                  `span${i.gote.nbOfPieces >= impasseNecessaryEnteredPieces ? '.good' : ''}`,
                  `${i.gote.nbOfPieces}/${impasseNecessaryEnteredPieces}`,
                ),
              ]),
              h('li', [
                `${i18n('totalImpasseValue')}: `,
                h(
                  `span${i.gote.pieceValue >= impasseNecessaryGoteScore ? '.good' : ''}`,
                  `${i.gote.pieceValue}/${impasseNecessaryGoteScore}`,
                ),
              ]),
            ]),
          ]),
        ]),
      ]),
    ],
  });
}

function username(player: Player): string {
  return player.user
    ? player.user.username
    : player.name || (player.ai ? engineNameFromCode(player.aiCode) : i18n('anonymousUser'));
}
