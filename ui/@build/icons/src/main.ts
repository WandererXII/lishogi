import { mkdir } from 'node:fs/promises';
import path from 'node:path';
import { getRootDir } from '@build/helpers/workspace-packages';
import { font } from './font.js';
import { sprites } from './sprites.js';

const svgSpriteCategs = ['study', 'tour'];

async function main() {
  const rootDir = await getRootDir();
  const outDir = path.join(rootDir, 'public/icons/');

  await mkdir(outDir, { recursive: true });

  await sprites(outDir, svgSpriteCategs);
  await font(rootDir, outDir);
}

await main();
