import menuSlowdown from 'common/menu-slowdown';
import { Shogiground } from 'shogiground';
import { analysis, practice, replay, study } from '../boot';
import type AnalyseCtrl from '../ctrl';
import makeCtrl from '../ctrl';
import type { AnalyseOpts } from '../interfaces';
import patch from '../patch';
import makeView from '../view';

function main(opts: AnalyseOpts): AnalyseCtrl {
  switch (opts.mode) {
    case 'replay':
      return replay(opts, start);
    case 'study':
      return study(opts, start);
    case 'practice':
      return practice(opts, start);
    default:
      return analysis(opts, start);
  }
}

function start(opts: AnalyseOpts): AnalyseCtrl {
  const element = document.querySelector('main.analyse') as HTMLElement;

  const ctrl = new makeCtrl(opts, redraw);

  element.innerHTML = '';
  let vnode = patch(element, makeView(ctrl));

  function redraw() {
    vnode = patch(vnode, makeView(ctrl));
  }

  menuSlowdown();

  return ctrl;
}

window.lishogi.registerModule(__bundlename__, main);

window.Shogiground = Shogiground;
