import { existsSync } from 'node:fs';
import * as fs from 'node:fs/promises';
import path from 'node:path';
import { getOutputDirectory } from '@build/helpers/util';
import { getRootDir } from '@build/helpers/workspace-packages';
import { font } from './font.js';
import { sprites } from './sprites.js';

const svgSpriteCategs = ['study', 'tour'];

async function main() {
  const rootDir = await getRootDir();
  const outDir = path.join(rootDir, getOutputDirectory(), 'icons/');

  if (existsSync(outDir)) await fs.rm(outDir, { recursive: true, force: true });
  await fs.mkdir(outDir, { recursive: true });

  await sprites(outDir, svgSpriteCategs);
  await font(rootDir, outDir);
}

await main();
