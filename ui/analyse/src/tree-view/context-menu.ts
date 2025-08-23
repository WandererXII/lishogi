import { icons } from 'common/icons';
import { bind, onInsert } from 'common/snabbdom';
import { i18n } from 'i18n';
import { h, type VNode } from 'snabbdom';
import type AnalyseCtrl from '../ctrl';
import patch from '../patch';
import * as studyView from '../study/study-view';
import { nodeFullName } from '../util';

interface Opts {
  path: Tree.Path;
  root: AnalyseCtrl;
}

interface Coords {
  x: number;
  y: number;
}

interface PageOrClientPos {
  pageX?: number;
  pageY?: number;
  clientX?: number;
  clientY?: number;
}

const elementId = 'analyse-cm';

function getPosition(e: MouseEvent | TouchEvent): Coords | null {
  let pos = e as PageOrClientPos;
  if ('touches' in e && e.touches.length > 0) pos = e.touches[0];
  if (pos.pageX || pos.pageY)
    return {
      x: pos.pageX!,
      y: pos.pageY!,
    };
  else if (pos.clientX || pos.clientY)
    return {
      x: pos.clientX! + document.body.scrollLeft + document.documentElement!.scrollLeft,
      y: pos.clientY! + document.body.scrollTop + document.documentElement!.scrollTop,
    };
  else return null;
}

function positionMenu(menu: HTMLElement, coords: Coords): void {
  const menuWidth = menu.offsetWidth + 4;
  const menuHeight = menu.offsetHeight + 4;
  const windowWidth = window.innerWidth;
  const windowHeight = window.innerHeight;

  menu.style.left =
    windowWidth - coords.x < menuWidth ? `${windowWidth - menuWidth}px` : `${coords.x}px`;

  menu.style.top =
    windowHeight - coords.y < menuHeight ? `${windowHeight - menuHeight}px` : `${coords.y}px`;
}

function action(icon: string, text: string, handler: () => void): VNode {
  return h(
    'a',
    {
      attrs: { 'data-icon': icon },
      hook: bind('click', handler),
    },
    text,
  );
}

function view(opts: Opts, coords: Coords): VNode {
  const ctrl = opts.root;
  const node = ctrl.tree.nodeAtPath(opts.path);
  const onMainline =
    ctrl.tree.pathIsMainline(opts.path) && !ctrl.tree.pathIsForcedVariation(opts.path);
  const cantChangeMainline = !!ctrl.study?.data.chapter.gameLength;
  return h(
    `div#${elementId}.visible`,
    {
      hook: {
        ...onInsert(elm => {
          elm.addEventListener('contextmenu', e => {
            e.preventDefault();
            return false;
          });
          positionMenu(elm, coords);
        }),
        postpatch: (_, vnode) => positionMenu(vnode.elm as HTMLElement, coords),
      },
    },
    [
      h('p.title.inlined', nodeFullName(node)),
      onMainline
        ? null
        : action(icons.up, i18n('promoteVariation'), () => ctrl.promote(opts.path, false)),
      onMainline || cantChangeMainline
        ? null
        : action(icons.correct, i18n('makeMainLine'), () => ctrl.promote(opts.path, true)),
      onMainline && cantChangeMainline
        ? null
        : action(icons.trashBin, i18n('deleteFromHere'), () => ctrl.deleteNode(opts.path)),
    ]
      .concat(ctrl.study ? studyView.contextMenu(ctrl.study, opts.path, node) : [])
      .concat([
        onMainline && !cantChangeMainline
          ? action(icons.exit, i18n('forceVariation'), () => ctrl.forceVariation(opts.path, true))
          : null,
      ]),
  );
}

export default function (e: MouseEvent, opts: Opts): void {
  let pos = getPosition(e);
  if (pos === null) {
    if (opts.root.contextMenuPath) return;
    pos = { x: 0, y: 0 };
  }

  const el = $(`#${elementId}`)[0] || $(`<div id="${elementId}">`).appendTo($('body'))[0];
  opts.root.contextMenuPath = opts.path;
  function close(e: MouseEvent) {
    if (e.button === 2) return; // right click
    opts.root.contextMenuPath = undefined;
    document.removeEventListener('click', close, false);
    $(`#${elementId}`).removeClass('visible');
    opts.root.redraw();
  }
  document.addEventListener('click', close, false);
  el.innerHTML = '';
  patch(el, view(opts, pos));
}
