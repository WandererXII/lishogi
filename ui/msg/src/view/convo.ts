import { hookMobileMousedown } from 'common/mobile';
import { type VNode, h } from 'snabbdom';
import type MsgCtrl from '../ctrl';
import type { Convo } from '../interfaces';
import renderActions from './actions';
import renderInteract from './interact';
import renderMsgs from './msgs';
import { userName } from './util';

export default function renderConvo(ctrl: MsgCtrl, convo: Convo): VNode {
  const user = convo.user;
  return h(
    'div.msg-app__convo',
    {
      key: user.id,
    },
    [
      h('div.msg-app__convo__head', [
        h('div.msg-app__convo__head__left', [
          h('span.msg-app__convo__head__back', {
            attrs: { 'data-icon': 'I' },
            hook: hookMobileMousedown(ctrl.showSide),
          }),
          h(
            'a.user-link.ulpt',
            {
              attrs: { href: `/@/${user.name}` },
              class: {
                online: user.online,
                offline: !user.online,
              },
            },
            [
              h(`i.line${user.id == 'lishogi' ? '.moderator' : user.patron ? '.patron' : ''}`),
              ...userName(user),
            ],
          ),
        ]),
        h('div.msg-app__convo__head__actions', renderActions(ctrl, convo)),
      ]),
      renderMsgs(ctrl, convo),
      h('div.msg-app__convo__reply', [
        convo.relations.out === false || convo.relations.in === false
          ? h(
              'div.msg-app__convo__reply__block.text',
              {
                attrs: { 'data-icon': 'k' },
              },
              'This conversation is blocked.',
            )
          : convo.postable
            ? renderInteract(ctrl, user)
            : h(
                'div.msg-app__convo__reply__block.text',
                {
                  attrs: { 'data-icon': 'k' },
                },
                `${user.name} doesn't accept new messages.`,
              ),
      ]),
    ],
  );
}
