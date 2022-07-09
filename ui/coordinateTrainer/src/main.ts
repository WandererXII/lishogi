import { init, attributesModule, eventListenersModule, classModule, propsModule, styleModule } from 'snabbdom';
import { menuHover } from 'common/menuHover';
import { Shogiground } from 'shogiground';

import view from './view';
import { CoordinateTrainerConfig } from './interfaces';
import CoordinateTrainerCtrl from './ctrl';

const patch = init([classModule, attributesModule, propsModule, eventListenersModule, styleModule]);

export default function LishogiCoordinateTrainer(element: HTMLElement, config: CoordinateTrainerConfig): void {
  const ctrl = new CoordinateTrainerCtrl(config, redraw);
  element.innerHTML = '';
  const inner = document.createElement('div');
  element.appendChild(inner);
  let vnode = patch(inner, view(ctrl));

  function redraw() {
    vnode = patch(vnode, view(ctrl));
  }

  menuHover();
}

// that's for the rest of lishogi to access shogiground
// without having to include it a second time
window.Shogiground = Shogiground;
