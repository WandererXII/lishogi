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
  NG: 'promotedsilver',
  NK: 'promotedknight',
  NY: 'promotedlance',
  OU: 'king',
  RY: 'dragon',
  TO: 'tokin',
  UM: 'horse',
};

function classesWithOrientation(color: string, role: string, flipped: boolean): string {
  if (flipped) {
    if (color === 'sente') {
      return dedent`.sg-wrap.orientation-gote piece.${role}.gote,
      .hand-bottom piece.${role}.gote,
      .spare-bottom piece.${role}.gote`;
    } else {
      return dedent`.sg-wrap.orientation-gote piece.${role}.sente,
      .hand-top piece.${role}.sente,
      .spare-top piece.${role}.sente`;
    }
  } else {
    if (color === 'sente') {
      return dedent`piece.${role}.sente,
      .sg-wrap.orientation-sente piece.${role}.sente,
      .hand-bottom piece.${role}.sente,
      .spare-bottom piece.${role}.sente`;
    } else {
      return dedent`piece.${role}.gote,
      .sg-wrap.orientation-sente piece.${role}.gote,
      .hand-top piece.${role}.gote,
      .spare-top piece.${role}.gote`;
    }
  }
}

function classes(color: string, role: string): string {
  if (color === 'sente') {
    // facing up
    if (role === 'king') {
      return dedent`.sg-wrap.orientation-gote piece.king.gote,
      .spare-bottom piece.king.gote`;
    } else if (role === 'tama') {
      return dedent`piece.king.sente,
      .sg-wrap.orientation-sente piece.king.sente`;
    } else {
      return dedent`piece.${role}.sente,
      .sg-wrap.orientation-sente piece.${role}.sente,
      .sg-wrap.orientation-gote piece.${role}.gote,
      .hand-bottom piece.${role}.gote,
      .spare-bottom piece.${role}.gote`;
    }
  } else {
    // facing down
    if (role === 'king') {
      return dedent`piece.king.gote,
      .sg-wrap.orientation-sente piece.king.gote`;
    } else if (role === 'tama') {
      return dedent`.sg-wrap.orientation-gote piece.king.sente,
      .spare-top piece.king.sente`;
    } else {
      return dedent`piece.${role}.gote,
      .sg-wrap.orientation-sente piece.${role}.gote,
      .sg-wrap.orientation-gote piece.${role}.sente,
      .hand-top piece.${role},
      .spare-top piece.${role}.sente`;
    }
  }
}

// piece set name: [set classes, class resets for other variants]
const pieceSetNameCls: Record<string, [string, string]> = {
  pixel: ['image-rendering: pixelated;', 'image-rendering: unset;'],
  dewitt_1kanji: ['background-size: contain;', 'background-size: cover;'],
  dewitt_2kanji: ['background-size: contain;', 'background-size: cover;'],
  dewitt_czech: ['background-size: contain;', 'background-size: cover;'],
  mnemonic: ['background-size: contain;', 'background-size: cover;'],
  better_8_bit: [
    'image-rendering: pixelated; background-size: contain; background-repeat: no-repeat;',
    'image-rendering: unset; background-size: cover;',
  ],
  characters: [
    'background-size: contain; background-repeat: no-repeat;',
    'background-size: cover;',
  ],
};

function extraCss(pieceSet: PieceSet): string {
  const cssClasses: string[] = [];

  // extension
  if (pieceSet.ext === 'png') {
    cssClasses.push('piece { will-change: transform; background-repeat: unset; }');
    cssClasses.push(
      '.v-chushogi piece, .v-kyotoshogi piece, .dasher piece  { will-change: auto; background-repeat: no-repeat; }',
    );
  }

  // name
  const cls = pieceSetNameCls[pieceSet.name];
  if (cls) {
    cssClasses.push(`piece { ${cls[0]} }`);
    cssClasses.push(`.v-chushogi piece, .v-kyotoshogi piece, .dasher piece  { ${cls[1]} }`);
  }

  cssClasses.push('.v-chushogi piece, .v-kyotoshogi piece{ background-image: none !important; }');
  return cssClasses.join('\n');
}

export function standard(sourceDir: string, destDir: string): void {
  const pieceSets = categorizePieceSets(sourceDir);
  const roles = Object.keys(roleDict);

  for (const pieceSet of pieceSets.regular) {
    const cssClasses = colors.flatMap(color =>
      roles.map(role => {
        const piece = `${color === 'sente' ? '0' : '1'}${role}`;
        const file = path.join(sourceDir, pieceSet.name, `${piece}.${pieceSet.ext}`);
        const base64 = readImageAsBase64(file);
        const cls = classes(color, roleDict[role]);
        return `${cls} {background-image:url('data:image/${types[pieceSet.ext]}${base64}')}`;
      }),
    );

    cssClasses.push(extraCss(pieceSet));
    cssClasses.push(''); // trailing new line

    fs.writeFileSync(path.join(destDir, `${pieceSet.name}.css`), cssClasses.join('\n'));
  }

  for (const pieceSet of pieceSets.bidirectional) {
    const cssClasses = ['-1', ''].flatMap(up =>
      colors.flatMap(color =>
        roles.map(role => {
          const piece = `${color === 'sente' ? '0' : '1'}${role}${up}`;
          const file = path.join(sourceDir, pieceSet.name, `${piece}.${pieceSet.ext}`);
          const base64 = readImageAsBase64(file);
          const cls = classesWithOrientation(color, roleDict[role], up.length !== 0);
          return `${cls} {background-image:url('data:image/${types[pieceSet.ext]}${base64}')}`;
        }),
      ),
    );

    cssClasses.push(extraCss(pieceSet));
    cssClasses.push(''); // trailing new line

    fs.writeFileSync(path.join(destDir, `${pieceSet.name}.css`), cssClasses.join('\n'));
  }

  const dasher: string[] = [];
  for (const pieceSet of [...pieceSets.regular, ...pieceSets.bidirectional]) {
    const file = path.join(sourceDir, pieceSet.name, `0KI.${pieceSet.ext}`);

    dasher.push(dasherCss(file, pieceSet, 'standard'));

    const cls = pieceSetNameCls[pieceSet.name];
    if (cls) dasher.push(dasherWrapCss(cls[0], pieceSet, 'standard'));
  }
  dasher.push(...specialDasher('standard', sourceDir));

  fs.writeFileSync(path.join(destDir, 'lishogi.dasher.css'), dasher.join('\n'));
}
