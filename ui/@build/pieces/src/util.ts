import { readdirSync, readFileSync, statSync } from 'node:fs';
import { basename, extname, join, sep } from 'node:path';
import type { CategorizedPieceSets, Ext, PieceSet, PieceSetVariant } from './types.js';

export const colors = ['sente', 'gote'] as const;
export const variants: PieceSetVariant[] = [
  'standard',
  'kyotoshogi',
  'chushogi',
  'dobutsu',
] as const;
export const types: Record<Ext, string> = {
  svg: 'svg+xml;base64,',
  png: 'png;base64,',
};

export function dasherWrapCss(value: string, pieceSet: PieceSet, variant: PieceSetVariant): string {
  return `#dasher_app .list.tab-${variant} .no-square[data-value='${pieceSet.name}'] piece{${value}}`;
}
export function dasherCss(path: string, pieceSet: PieceSet, variant: PieceSetVariant): string {
  const base64 = readImageAsBase64(path);
  return dasherWrapCss(
    `background-image:url('data:image/${types[pieceSet.ext]}${base64}');`,
    pieceSet,
    variant,
  );
}

export function readImageAsBase64(path: string): string {
  try {
    const image = readFileSync(path);
    return image.toString('base64');
  } catch {
    const parts = path.split(sep);
    const [set, name] = parts.slice(-2);
    console.error('Missing:', set, name);
    return '';
  }
}

export function categorizePieceSets(directoryPath: string): CategorizedPieceSets {
  const directories = readdirSync(directoryPath).filter(file =>
    statSync(join(directoryPath, file)).isDirectory(),
  );

  const categorized: CategorizedPieceSets = { regular: [], bidirectional: [] };

  directories.forEach(dir => {
    const dirPath = join(directoryPath, dir);
    const files = readdirSync(dirPath);

    const pngFiles = files.filter(file => extname(file) === '.png');
    const svgFiles = files.filter(file => extname(file) === '.svg');

    if (
      (pngFiles.length > 0 && svgFiles.length > 0) ||
      (pngFiles.length === 0 && svgFiles.length === 0)
    ) {
      throw new Error(`Provide either 'png' or 'svg' pieces: ${dir}`);
    }

    const ext: Ext = pngFiles.length > 0 ? 'png' : 'svg';

    const hasBidirectional = files.some(file => basename(file, extname(file)).endsWith('-1'));

    if (hasBidirectional) {
      categorized.bidirectional.push({ name: dir, ext });
    } else {
      categorized.regular.push({ name: dir, ext });
    }
  });

  return categorized;
}
