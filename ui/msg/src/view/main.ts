import spinner from 'common/spinner';
import { type VNode, h } from 'snabbdom';
import type MsgCtrl from '../ctrl';
import renderContact from './contact';
import renderConvo from './convo';
import * as search from './search';

export default function (ctrl: MsgCtrl): VNode {
  const activeId = ctrl.data.convo?.user.id;
  return h(
    'main.box.msg-app',
    {
      class: {
        [`pane-${ctrl.pane}`]: true,
      },
    },
    [
      h('div.msg-app__side', [
        search.renderInput(ctrl),
        ctrl.search.result
          ? search.renderResults(ctrl, ctrl.search.result)
          : h(
              'div.msg-app__contacts.msg-app__side__content',
              ctrl.data.contacts.map(t => renderContact(ctrl, t, activeId)),
            ),
      ]),
      ctrl.data.convo
        ? renderConvo(ctrl, ctrl.data.convo)
        : ctrl.loading
          ? h('div.msg-app__convo', { key: ':' }, [h('div.msg-app__convo__head'), spinner()])
          : '',
    ],
  );
}
