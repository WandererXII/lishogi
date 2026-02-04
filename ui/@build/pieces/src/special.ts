import * as fs from 'node:fs';
import * as path from 'node:path';
import dedent from 'dedent';
import type { PieceSet, PieceSetVariant } from './types.js';
import { dasherCss, readImageAsBase64, types } from './util.js';

const variantSelectors: Record<PieceSetVariant, string> = {
  standard: '',
  dobutsu: '.v-dobutsu',
  chushogi: '.v-chushogi',
  kyotoshogi: '.v-kyotoshogi',
};

export function special(variant: PieceSetVariant, sourceDir: string, destDir: string): void {
  const selector = variantSelectors[variant];
  const important = variant === 'standard' ? '' : ' !important';

  const invisibleCss = `${selector} piece { background-image: none${important}; }`;
  fs.writeFileSync(path.join(destDir, variant, 'invisible.css'), invisibleCss);

  const blankUp = readImageAsBase64(path.join(sourceDir, 'special', '0_Blank.svg'));
  const blankUpCss = dedent`
                ${selector} piece.sente,
                ${selector} .sg-wrap.orientation-gote piece.gote,
                .spare-bottom${selector} piece.gote {
                  background-image:url('data:image/${types.svg}${blankUp}')${important}
                }`;
  const blankDown = readImageAsBase64(path.join(sourceDir, 'special', '1_Blank.svg'));
  const blankDownCss = dedent`
                ${selector} piece.gote,
                ${selector} .sg-wrap.orientation-gote piece.sente,
                .spare-top${selector} piece.sente {
                  background-image:url('data:image/${types.svg}${blankDown}')${important}
                }`;

  fs.writeFileSync(path.join(destDir, variant, 'blank.css'), [blankUpCss, blankDownCss].join('\n'));
}

export function specialDasher(variant: PieceSetVariant, sourceDir: string): string[] {
  sourceDir = path.dirname(sourceDir); // we get variant dir
  const dasher: string[] = [];

  const invisble: PieceSet = { name: 'invisible', ext: 'svg' };
  const invisibleFile = path.join(sourceDir, 'special', `invisible.${invisble.ext}`);
  dasher.push(dasherCss(invisibleFile, invisble, variant));

  const blank: PieceSet = { name: 'blank', ext: 'svg' };
  const blankFile = path.join(sourceDir, 'special', `0_Blank.${blank.ext}`);
  dasher.push(dasherCss(blankFile, blank, variant));

  return dasher;
}
