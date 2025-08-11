import { Shogiground } from 'shogiground';
import { attributesModule, classModule, init } from 'snabbdom';
import boot from '../boot';
import type LobbyController from '../ctrl';
import makeCtrl from '../ctrl';
import type { LobbyOpts } from '../interfaces';
import view from '../view/main';

const patch = init([classModule, attributesModule]);

function main(opts: LobbyOpts): LobbyController {
  return boot(opts, start);
}

function start(opts: LobbyOpts): LobbyController {
  const element = document.querySelector('.lobby__app')!;

  const ctrl = new makeCtrl(opts, redraw);

  element.innerHTML = '';
  let vnode = patch(element, view(ctrl));

  function redraw() {
    vnode = patch(vnode, view(ctrl));
  }

  return ctrl;
}

window.lishogi.registerModule(__bundlename__, main);

window.Shogiground = Shogiground;
