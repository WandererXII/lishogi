import { isCol1 } from 'common/mobile';
import { type StoredProp, storedProp } from 'common/storage';
import type { VNode } from 'snabbdom';
import type AnalyseCtrl from '../ctrl';
import type { ConcealOf } from '../interfaces';
import column from './column-view';
import inline from './inline-view';

type TreeViewKey = 'column' | 'inline';

export interface TreeView {
  get: StoredProp<TreeViewKey>;
  set(inline: boolean): void;
  toggle(): void;
  inline(): boolean;
}

export function ctrl(initialValue: TreeViewKey = 'column'): TreeView {
  const value = storedProp<TreeViewKey>('analyse.treeView', initialValue);
  function inline() {
    return value() === 'inline';
  }
  function set(i: boolean) {
    value(i ? 'inline' : 'column');
  }
  return {
    get: value,
    set,
    toggle() {
      set(!inline());
    },
    inline,
  };
}

// entry point, dispatching to selected view
export function render(ctrl: AnalyseCtrl, concealOf?: ConcealOf): VNode {
  return (ctrl.treeView.inline() || isCol1()) && !concealOf
    ? inline(ctrl)
    : column(ctrl, concealOf);
}
