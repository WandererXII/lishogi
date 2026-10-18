import { icons } from 'common/icons';
import { bind } from 'common/snabbdom';
import { i18n, i18nFormat } from 'i18n';
import { h, type VNode } from 'snabbdom';
import type MsgCtrl from '../ctrl';
import type { Convo } from '../interfaces';

export default function renderActions(ctrl: MsgCtrl, convo: Convo): VNode[] {
  if (convo.user.id == 'lishogi') return [];
  const nodes = [];
  const cls = 'msg-app__convo__action.button.button-empty';
  nodes.push(
    h(`a.${cls}.play`, {
      key: 'play',
      attrs: {
        'data-icon': icons.challenge,
        href: `/?user=${convo.user.name}#friend`,
        title: i18n('challengeToPlay'),
      },
    }),
  );
  nodes.push(h('div.msg-app__convo__action__sep', '|'));
  if (convo.relations.out === false)
    nodes.push(
      h(
        `button.${cls}.text`,
        {
          key: 'unblock',
          attrs: {
            'data-icon': icons.forbidden,
            title: i18n('unblock'),
            type: 'button',
          },
          hook: bind('click', ctrl.unblock),
        },
        i18n('blocked'),
      ),
    );
  else
    nodes.push(
      h(`button.${cls}.bad`, {
        key: 'block',
        attrs: {
          'data-icon': icons.forbidden,
          type: 'button',
          title: i18n('block'),
        },
        hook: bind('click', withConfirm(ctrl.block)),
      }),
    );
  nodes.push(
    h(`button.${cls}.bad`, {
      key: 'delete',
      attrs: {
        'data-icon': icons.trashBin,
        type: 'button',
        title: i18n('delete'),
      },
      hook: bind('click', withConfirm(ctrl.delete)),
    }),
  );
  if (convo.msgs[0])
    nodes.push(
      h(`button.${cls}.bad`, {
        key: 'report',
        attrs: {
          'data-icon': icons.warning,
          title: i18nFormat('reportXToModerators', convo.user.name),
        },
        hook: bind('click', withConfirm(ctrl.report)),
      }),
    );
  return nodes;
}

const withConfirm = (f: () => void) => (e: Event) => {
  if (confirm(`${(e.target as HTMLElement).getAttribute('title') || 'Confirm'}?`)) f();
};
