import { icons } from 'common/icons';
import { i18n, i18nFormatCapitalized } from 'i18n';
import { colorName } from 'shogi/color-name';
import { handRoles } from 'shogiops/position/util';
import { initialSfen } from 'shogiops/sfen';
import { opposite } from 'shogiops/util';
import { h, type VNode } from 'snabbdom';
import type EditorCtrl from '../ctrl';
import type { EditorState } from '../interfaces';

export function actions(ctrl: EditorCtrl, state: EditorState): VNode {
  return h('div.actions', [
    initialPosition(ctrl, state),
    clearBoard(ctrl, state),
    fillGotesHand(ctrl),
    flipBoard(ctrl),
  ]);
}

function initialPosition(ctrl: EditorCtrl, state: EditorState): VNode {
  return h(
    'span.action.text',
    {
      attrs: { 'data-icon': icons.first },
      class: {
        disabled: state.sfen === initialSfen(ctrl.rules),
      },
      on: {
        click() {
          ctrl.startPosition();
        },
      },
    },
    i18n('startPosition'),
  );
}

function clearBoard(ctrl: EditorCtrl, state: EditorState): VNode {
  return h(
    'span.action.text',
    {
      attrs: { 'data-icon': icons.trashBin },
      class: {
        disabled: /^[0-9/]+$/.test(state.sfen.split(' ')[0]) && state.sfen.split(' ')[2] === '-',
      },
      on: {
        click() {
          ctrl.clearBoard();
        },
      },
    },
    i18n('clearBoard'),
  );
}

function fillGotesHand(ctrl: EditorCtrl): VNode | null {
  return handRoles(ctrl.rules).length === 0
    ? null
    : h(
        'span.action.text',
        {
          attrs: { 'data-icon': icons.up },
          class: {
            disabled: !ctrl.canFillGoteHand(),
          },
          on: {
            click() {
              ctrl.fillGotesHand();
            },
          },
        },
        i18nFormatCapitalized('fillXHand', colorName('gote', false)),
      );
}

function flipBoard(ctrl: EditorCtrl): VNode {
  return h(
    'span.action.text',
    {
      class: { active: ctrl.shogiground.state.orientation === 'gote' },
      attrs: { 'data-icon': icons.loop },
      on: {
        click() {
          ctrl.setOrientation(opposite(ctrl.shogiground.state.orientation));
        },
      },
    },
    `${i18n('flipBoard')} (${colorName(ctrl.shogiground.state.orientation, false)})`,
  );
}
