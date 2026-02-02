import * as fs from 'node:fs';
import * as path from 'node:path';
import dedent from 'dedent';
import { specialDasher } from './special.js';
import type { PieceSet } from './types.js';
import {
  categorizePieceSets,
  colors,
  dasherCss,
  dasherWrapCss,
  readImageAsBase64,
  types,
} from './util.js';

const roles = [
  'lance',
  'leopard',
  'copper',
  'silver',
  'gold',
  'elephant',
  'chariot',
  'bishop',
  'tiger',
  'phoenix',
  'kirin',
  'sidemover',
  'verticalmover',
  'rook',
  'horse',
  'dragon',
  'queen',
  'lion',
  'pawn',
  'gobetween',
  'king',
  'tama',
  'promotedpawn',
  'ox',
  'stag',
  'boar',
  'falcon',
  'prince',
  'eagle',
  'whale',
  'whitehorse',
  'dragonpromoted',
  'horsepromoted',
  'lionpromoted',
  'queenpromoted',
  'bishoppromoted',
  'elephantpromoted',
  'sidemoverpromoted',
  'verticalmoverpromoted',
  'rookpromoted',
];

function sameUpDownM(role: string): boolean {
  return [
    'bishop',
    'bishoppromoted',
    'boar',
    'chariot',
    'dragon',
    'dragonpromoted',
    'horse',
    'horsepromoted',
    'king',
    'kirin',
    'leopard',
    'lion',
    'lionpromoted',
    'ox',
    'phoenix',
    'prince',
    'queen',
    'queenpromoted',
    'rook',
    'rookpromoted',
    'sidemover',
    'sidemoverpromoted',
    'stag',
    'tama',
    'verticalmover',
    'verticalmoverpromoted',
  ].includes(role);
}

function classesMnemonic(color: string, role: string): string {
  return `.v-chushogi piece.${role}.${color}`;
}

function classesWithOrientation(color: string, role: string, flipped: boolean): string {
  if (flipped) {
    if (color === 'sente') {
      return dedent`.v-chushogi .sg-wrap.orientation-gote piece.${role}.gote,
      .spare-bottom.v-chushogi piece.${role}.gote`;
    } else {
      return dedent`.v-chushogi .sg-wrap.orientation-gote piece.${role}.sente,
      .spare-top.v-chushogi piece.${role}.sente`;
    }
  } else {
    if (color === 'sente') {
      return dedent`.v-chushogi piece.${role}.sente,
      .v-chushogi .sg-wrap.orientation-sente piece.${role}.sente,
      .spare-bottom.v-chushogi piece.${role}.sente`;
    } else {
      return dedent`.v-chushogi piece.${role}.gote,
      .v-chushogi .sg-wrap.orientation-sente piece.${role}.gote,
      .spare-top.v-chushogi piece.${role}.gote`;
    }
  }
}

function classes(color: string, role: string): string {
  if (color === 'sente') {
    // facing up
    if (role === 'king') {
      return dedent`.v-chushogi .sg-wrap.orientation-gote piece.king.gote,
      .spare-bottom.v-chushogi piece.king`;
    } else if (role === 'tama') {
      return dedent`.v-chushogi piece.king.sente,
      .v-chushogi .sg-wrap.orientation-sente piece.king.sente,
      .spare-bottom.v-chushogi piece.king.sente`;
    } else {
      return dedent`.v-chushogi .sg-wrap.orientation-sente piece.${role}.sente,
      .v-chushogi .sg-wrap.orientation-gote piece.${role}.gote,
      .spare-bottom.v-chushogi piece.${role}`;
    }
  } else {
    // facing down
    if (role === 'king') {
      return dedent`.v-chushogi .sg-wrap.orientation-sente piece.king.gote,
      .spare-top.v-chushogi piece.king`;
    } else if (role === 'tama') {
      return dedent`.v-chushogi .sg-wrap.orientation-gote piece.king.sente,
      .spare-top.v-chushogi piece.king.sente`;
    } else {
      return dedent`.v-chushogi .sg-wrap.orientation-sente piece.${role}.gote,
      .v-chushogi .sg-wrap.orientation-gote piece.${role}.sente,
      .spare-top.v-chushogi piece.${role}`;
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
      '.v-chushogi piece { will-change: transform !important; background-repeat: unset !important; }',
    );
  } else {
    cssClasses.push('.v-chushogi piece { will-change: auto; background-repeat: no-repeat; }');
  }

  // name
  const cls = pieceSetNameCls[pieceSet.name];
  if (cls) {
    cssClasses.push(`.v-chushogi piece { ${cls} }`);
  }

  return cssClasses.join('\n');
}

export function chushogi(sourceDir: string, destDir: string): void {
  const pieceSets = categorizePieceSets(sourceDir);

  for (const pieceSet of pieceSets.regular) {
    const cssClasses = colors.flatMap(color =>
      roles.map(role => {
        const piece = `${color === 'sente' ? '0_' : '1_'}${role.toUpperCase()}`;
        const file = path.join(sourceDir, pieceSet.name, `${piece}.${pieceSet.ext}`);
        const base64 = readImageAsBase64(file);
        return `${classes(color, role)} {background-image:url('data:image/${types[pieceSet.ext]}${base64}') !important;}`;
      }),
    );

    cssClasses.push(extraCss(pieceSet));
    cssClasses.push(''); // trailing new line

    fs.writeFileSync(path.join(destDir, `${pieceSet.name}.css`), cssClasses.join('\n'));
  }

  for (const pieceSet of pieceSets.bidirectional) {
    const rolesWithoutTama = roles.filter(role => role !== 'tama');
    const cssClasses = ['-1', '']
      .flatMap(up =>
        colors.flatMap(color =>
          rolesWithoutTama.map(role => {
            const piece = `${color === 'sente' ? '0_' : '1_'}${role.toUpperCase()}${up}`;
            const file = path.join(sourceDir, pieceSet.name, `${piece}.${pieceSet.ext}`);

            if (!(pieceSet.name === 'mnemonic' && up === '-1' && sameUpDownM(role))) {
              const base64 = readImageAsBase64(file);

              if (sameUpDownM(role) && pieceSet.name === 'mnemonic') {
                return `${classesMnemonic(color, role)} {background-image:url('data:image/${types[pieceSet.ext]}${base64}') !important;}`;
              } else {
                return `${classesWithOrientation(color, role, up.length !== 0)} {background-image:url('data:image/${types[pieceSet.ext]}${base64}') !important;}`;
              }
            }
            return '';
          }),
        ),
      )
      .filter(css => !!css);

    cssClasses.push(extraCss(pieceSet));
    cssClasses.push(''); // trailing new line

    fs.writeFileSync(path.join(destDir, `${pieceSet.name}.css`), cssClasses.join('\n'));
  }

  const dasher: string[] = [];
  for (const pieceSet of [...pieceSets.regular, ...pieceSets.bidirectional]) {
    const file = path.join(sourceDir, pieceSet.name, `0_KIRIN.${pieceSet.ext}`);

    dasher.push(dasherCss(file, pieceSet, 'chushogi'));

    const cls = pieceSetNameCls[pieceSet.name];
    if (cls) dasher.push(dasherWrapCss(cls, pieceSet, 'chushogi'));
  }
  dasher.push(...specialDasher('chushogi', sourceDir));

  fs.writeFileSync(path.join(destDir, 'lishogi.dasher.css'), dasher.join('\n'));
}
