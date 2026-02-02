import * as fs from 'node:fs';
import * as path from 'node:path';
import dedent from 'dedent';
import { specialDasher } from './special.js';
import type { PieceSet, RoleDict } from './types.js';
import {
  categorizePieceSets,
  colors,
  dasherCss,
  dasherWrapCss,
  readImageAsBase64,
  types,
} from './util.js';

const roleDict: RoleDict = {
  FU: 'pawn',
  GI: 'silver',
  GY: 'tama',
  HI: 'rook',
  KA: 'bishop',
  KE: 'knight',
  KI: 'gold',
  KY: 'lance',
  OU: 'king',
  TO: 'tokin',
};

function classesWithOrientation(color: string, role: string, flipped: boolean): string {
  if (flipped) {
    if (color === 'sente') {
      return dedent`.v-kyotoshogi .sg-wrap.orientation-gote piece.${role}.gote,
      .v-kyotoshogi .hand-bottom piece.${role}.gote,
      .spare-bottom.v-kyotoshogi piece.${role}.gote`;
    } else {
      return dedent`.v-kyotoshogi .sg-wrap.orientation-gote piece.${role}.sente,
      .v-kyotoshogi .hand-top piece.${role}.sente,
      .spare-top.v-kyotoshogi piece.${role}.sente`;
    }
  } else {
    if (color === 'sente') {
      return dedent`.v-kyotoshogi .sg-wrap.orientation-sente piece.${role}.sente,
      .v-kyotoshogi .hand-bottom piece.${role}.sente,
      .spare-bottom.v-kyotoshogi piece.${role}.sente`;
    } else {
      return dedent`.sg-wrap.orientation-sente piece.${role}.gote,
      .v-kyotoshogi .hand-top piece.${role}.gote,
      .spare-top.v-kyotoshogi piece.${role}.gote`;
    }
  }
}

function classes(color: string, role: string): string {
  if (color === 'sente') {
    // facing up
    if (role === 'king') {
      return dedent`.v-kyotoshogi .sg-wrap.orientation-gote piece.king.gote,
      .spare-bottom.v-kyotoshogi piece.king.gote`;
    } else if (role === 'tama') {
      return dedent`.v-kyotoshogi piece.king.sente,
      .v-kyotoshogi .sg-wrap.orientation-sente piece.king.sente,
      .spare-bottom.v-kyotoshogi piece.king.sente`;
    } else {
      return dedent`.v-kyotoshogi piece.${role}.sente,
      .v-kyotoshogi .sg-wrap.orientation-sente piece.${role}.sente,
      .v-kyotoshogi .sg-wrap.orientation-gote piece.${role}.gote,
      .v-kyotoshogi .hand-bottom piece.${role}.gote,
      .spare-bottom.v-kyotoshogi piece.${role}`;
    }
  } else {
    // facing down
    if (role === 'king') {
      return dedent`.v-kyotoshogi piece.king.gote,
      .v-kyotoshogi .sg-wrap.orientation-sente piece.king.gote,
      .spare-top.v-kyotoshogi piece.king.gote`;
    } else if (role === 'tama') {
      return dedent`.v-kyotoshogi .sg-wrap.orientation-gote piece.king.sente,
      .spare-top.v-kyotoshogi piece.king.sente`;
    } else {
      return dedent`.v-kyotoshogi piece.${role}.gote,
      .v-kyotoshogi .sg-wrap.orientation-sente piece.${role}.gote,
      .v-kyotoshogi .sg-wrap.orientation-gote piece.${role}.sente,
      .v-kyotoshogi .hand-top piece.${role},
      .spare-top.v-kyotoshogi piece.${role}`;
    }
  }
}

// piece set name: [set classes]
const pieceSetNameCls: Record<string, string> = {
  dewitt_1kanji: 'background-size: contain !important;',
  dewitt_2kanji: 'background-size: contain !important;',
  dewitt_czech: 'background-size: contain !important;',
  greenwade: 'background-size: contain !important;',
  mnemonic: 'background-size: contain !important;',
  better_8_bit:
    'image-rendering: pixelated !important; background-size: contain !important; background-repeat: no-repeat !important;',
};

function extraCss(pieceSet: PieceSet): string {
  const cssClasses: string[] = [];

  // extension
  if (pieceSet.ext === 'png') {
    cssClasses.push(
      '.v-kyotoshogi piece { will-change: transform !important; background-repeat: unset !important; }',
    );
  }

  // name
  const cls = pieceSetNameCls[pieceSet.name];
  if (cls) {
    cssClasses.push(`.v-kyotoshogi piece { ${cls} }`);
  }

  return cssClasses.join('\n');
}

export function kyotoshogi(sourceDir: string, destDir: string): void {
  const pieceSets = categorizePieceSets(sourceDir);
  const roles = Object.keys(roleDict);

  for (const pieceSet of pieceSets.regular) {
    const cssClasses = colors.flatMap(color =>
      roles.map(role => {
        const piece = `${color === 'sente' ? '0' : '1'}${role}`;
        const file = path.join(sourceDir, pieceSet.name, `${piece}.${pieceSet.ext}`);
        const base64 = readImageAsBase64(file);
        const cls = classes(color, roleDict[role]);
        return `${cls} {background-image:url('data:image/${types[pieceSet.ext]}${base64}')!important;}`;
      }),
    );

    cssClasses.push(extraCss(pieceSet));
    cssClasses.push(''); // trailing new line

    fs.writeFileSync(path.join(destDir, `${pieceSet.name}.css`), cssClasses.join('\n'));
  }

  for (const pieceSet of pieceSets.bidirectional) {
    const cssClasses = ['-1', '']
      .flatMap(up =>
        colors.flatMap(color =>
          roles.map(role => {
            const piece = `${color === 'sente' ? '0' : '1'}${role}${up}`;
            const file = path.join(sourceDir, pieceSet.name, `${piece}.${pieceSet.ext}`);
            const base64 = readImageAsBase64(file);
            const cls = classesWithOrientation(color, roleDict[role], up.length !== 0);
            return `${cls} {background-image:url('data:image/${types[pieceSet.ext]}${base64}')!important;}`;
          }),
        ),
      )
      .filter(css => css !== '');

    cssClasses.push(extraCss(pieceSet));
    cssClasses.push(''); // trailing new line

    fs.writeFileSync(path.join(destDir, `${pieceSet.name}.css`), cssClasses.join('\n'));
  }

  const dasher: string[] = [];
  for (const pieceSet of [...pieceSets.regular, ...pieceSets.bidirectional]) {
    const file = path.join(sourceDir, pieceSet.name, `0KY.${pieceSet.ext}`);

    dasher.push(dasherCss(file, pieceSet, 'kyotoshogi'));

    const cls = pieceSetNameCls[pieceSet.name];
    if (cls) dasher.push(dasherWrapCss(cls, pieceSet, 'kyotoshogi'));
  }
  dasher.push(...specialDasher('kyotoshogi', sourceDir));

  fs.writeFileSync(path.join(destDir, 'lishogi.dasher.css'), dasher.join('\n'));
}
