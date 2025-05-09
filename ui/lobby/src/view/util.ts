import type { MaybeVNodes } from 'common/snabbdom';
import { h } from 'snabbdom';

export function tds(bits: MaybeVNodes): MaybeVNodes {
  return bits.map(bit => h('td', [bit]));
}
