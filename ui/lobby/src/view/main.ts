import { icons } from 'common/icons';
import { dataIcon, type MaybeVNode, type MaybeVNodes } from 'common/snabbdom';
import spinner from 'common/spinner';
import { h, type VNode, type VNodeData } from 'snabbdom';
import type LobbyController from '../ctrl';
import { setupModal } from '../setup/view';
import renderPlaying from './playing';
import * as renderPresets from './presets';
import renderTable from './table/main';
import renderTabs from './tabs';

export default function (ctrl: LobbyController): VNode {
  let body: MaybeVNodes | MaybeVNode;
  let data: VNodeData = {};
  if (ctrl.redirecting) body = spinner();
  else
    switch (ctrl.tab) {
      case 'presets':
        body = renderPresets.render(ctrl);
        data = { hook: renderPresets.presetHooks(ctrl) };
        break;
      case 'real_time':
      case 'seeks':
        body = renderTable(ctrl);
        data = { attrs: dataIcon(icons.logo) };
        break;
      case 'now_playing':
        body = renderPlaying(ctrl);
        break;
    }
  return h(`div.lobby__app.lobby__app-${ctrl.tab}`, [
    h('div.tabs-horiz', renderTabs(ctrl)),
    h(`div.lobby__app__content.l${ctrl.redirecting ? 'redir' : ctrl.tab}`, data, body),
    ctrl.setupCtrl.isOpen ? setupModal(ctrl.setupCtrl) : undefined,
  ]);
}
