import * as fs from 'node:fs/promises';
import * as path from 'node:path';
import { getOutputDirectory } from '@build/helpers/util';
import { getRootDir } from '@build/helpers/workspace-packages';
import { copyLocalPackage, copyVendorPackage } from './util.js';

const vendorsJs: [string, string[]][] = [
  ['balloon-css', ['balloon.min.css']],
  ['howler', ['dist/howler.core.min.js']],
  ['jquery', ['dist/jquery.min.js']],
  ['spectrum-vanilla', ['dist/spectrum.min.js', 'dist/spectrum.min.css']],
  ['fairy-stockfish-nnue.wasm', ['stockfish.js', 'stockfish.wasm', 'stockfish.worker.js']],
  ['shogiground', ['dist/shogiground.min.js']],
  ['jquery-powertip', ['dist/jquery.powertip.min.js']],
  ['infinite-scroll', ['dist/infinite-scroll.pkgd.min.js']],
  ['sortablejs', ['Sortable.min.js']],
  ['@yaireo/tagify', ['dist/tagify.js']],
];

async function main(): Promise<void> {
  try {
    const rootDir = await getRootDir();
    const baseDestFolder = path.join(rootDir, getOutputDirectory(), 'vendors');

    await fs.rm(baseDestFolder, { recursive: true, force: true });
    await fs.mkdir(baseDestFolder, { recursive: true });

    await Promise.all(
      vendorsJs.map(([packageName, fileNames]) =>
        copyVendorPackage(packageName, fileNames, baseDestFolder),
      ),
    );

    const localJs: string[] = ['fipr', 'yaneuraou.k-p', 'typeahead'];

    await Promise.all(localJs.map(pkg => copyLocalPackage(pkg, baseDestFolder)));

    process.exit(0);
  } catch (err) {
    console.error(err);
    process.exit(1);
  }
}

await main();
