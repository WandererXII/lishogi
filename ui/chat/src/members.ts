import { icons } from 'common/icons';
import { dataIcon, onInsert } from 'common/snabbdom';
import { h, type VNode } from 'snabbdom';

export function studyMembers(): VNode {
  return h(
    'div.chat__members.none',
    {
      hook: onInsert(el => $(el).watchers()),
      attrs: { 'aria-live': 'off' },
    },
    [line('contributors', icons.person), line('viewer', icons.view)],
  );
}

export function gameMembers(): VNode {
  return h(
    'div.chat__members.none',
    {
      hook: onInsert(el => $(el).watchers()),
      attrs: { 'aria-live': 'off' },
    },
    [line('game', icons.randomColor), line('analysis', icons.microscope)],
  );
}

export function members(): VNode {
  return h(
    'div.chat__members.none',
    {
      hook: onInsert(el => $(el).watchers()),
      attrs: { 'aria-live': 'off' },
    },
    [line('default', icons.person)],
  );
}

const line = (cls: string, icon: string) =>
  h(`div.line.${cls}.none`, [
    h('span.number', { attrs: dataIcon(icon) }, '0'),
    ' ',
    h('span.list'),
  ]);
