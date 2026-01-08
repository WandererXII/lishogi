import { mkdir, rm } from 'node:fs/promises';
import * as path from 'node:path';
import { getRootDir } from '@build/helpers/workspace-packages';
import { chushogi } from './chushogi.js';
import { dobutsu } from './dobutsu.js';
import { kyotoshogi } from './kyotoshogi.js';
import { special } from './special.js';
import { standard } from './standard.js';
import type { PieceSetVariant } from './types.js';
import { variants } from './util.js';

async function main() {
  try {
    const rootDir = await getRootDir();
    const assetsDir = path.join(import.meta.dirname, '../assets');
    const baseDestDir = path.join(rootDir, 'public/piece-css/');

    function build(variant: PieceSetVariant): void {
      const fn =
        variant === 'chushogi'
          ? chushogi
          : variant === 'kyotoshogi'
            ? kyotoshogi
            : variant === 'dobutsu'
              ? dobutsu
              : standard;

      fn(path.join(assetsDir, variant), path.join(baseDestDir, variant));

      console.log(`Generated ${variant} piece css`);
    }

    await rm(baseDestDir, { recursive: true, force: true });
    await Promise.all(variants.map(v => mkdir(path.join(baseDestDir, v), { recursive: true })));

    variants.forEach(v => {
      build(v);
      special(v, assetsDir, baseDestDir);
    });

    process.exit(0);
  } catch (err) {
    console.error(err);
    process.exit(1);
  }
}

await main();
