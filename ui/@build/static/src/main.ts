import { existsSync } from 'node:fs';
import * as fs from 'node:fs/promises';
import * as path from 'node:path';
import { getOutputDirectory } from '@build/helpers/util';
import { getRootDir } from '@build/helpers/workspace-packages';

const dirname = path.dirname(import.meta.dirname);

async function main() {
  const srcDir = path.join(dirname, 'assets');

  const rootDir = await getRootDir();
  const destDir = path.join(rootDir, getOutputDirectory());

  await fs.mkdir(destDir, { recursive: true });

  const entries = await fs.readdir(srcDir, { withFileTypes: true });

  for (const entry of entries) {
    const srcPath = path.join(srcDir, entry.name);
    const destPath = path.join(destDir, entry.name);

    if (existsSync(destPath)) await fs.rm(destPath, { recursive: true, force: true });

    await fs.cp(srcPath, destPath, {
      recursive: entry.isDirectory(),
      force: true,
    });
    console.log(`Copied assets/${entry.name}`);
  }
}

await main();
