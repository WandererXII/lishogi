import { MaybeVNodes } from 'common/snabbdom';
import { h } from 'snabbdom';

export function tds(bits: MaybeVNodes): MaybeVNodes {
  return bits.map(function (bit) {
    return h('td', [bit]);
  });
}

export const perfIcons = {
  Blitz: ')',
  UltraBullet: '{',
  Bullet: 'T',
  Classical: '+',
  Rapid: '#',
  Minishogi: ',',
  Chushogi: '(',
  Correspondence: ';',
};
