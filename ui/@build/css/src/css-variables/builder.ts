import * as fs from 'node:fs/promises';
import * as path from 'node:path';
import { glob } from '@build/helpers/glob';
import type { Project } from '@build/helpers/workspace-packages';
import { generateColorMixVariables } from './generators/color-mix.js';
import { generateScssCssMap } from './generators/scss-css-map.js';
import { generateThemeVariables } from './generators/theme.js';
import { extractFromFile } from './parsers/extractor.js';
import { parseThemes } from './parsers/theme.js';

type CssVariableBuilder = {
  build: () => Promise<void>;
  update: (filepath: string) => Promise<boolean>;
};

export async function createCssVariableBuilder(
  rootDir: string,
  pkgs: Project[],
): Promise<CssVariableBuilder> {
  const themeDir = path.join(
    pkgs.find(p => p.manifest.name === 'common')!.rootDirRealPath,
    'style/theme',
  );
  const outDir = path.join(themeDir, 'generated');

  await fs.rm(outDir, { recursive: true, force: true });
  await fs.mkdir(outDir, { recursive: true });

  const allFiles = await glob(pkgs.map(p => path.join(p.rootDirRealPath, '/style/**/*.scss')));
  const extractedByFile = new Map<string, Set<string>>();
  for (const f of allFiles.filter(f => !f.includes(outDir))) {
    extractedByFile.set(f, await extractFromFile(f));
  }

  const build = async () => {
    const byThemeVaribles = await parseThemes(themeDir);

    const allExtracted = new Set<string>();
    for (const values of extractedByFile.values()) {
      for (const value of values) {
        allExtracted.add(value);
      }
    }
    const extractedSorted = Array.from(allExtracted).toSorted();

    await generateScssCssMap(rootDir, byThemeVaribles, extractedSorted, outDir);
    await generateThemeVariables(rootDir, byThemeVaribles, extractedSorted, outDir);
    await generateColorMixVariables(rootDir, extractedSorted, outDir);
    console.log('Generated theme variables');
  };

  const update = async (filepath: string) => {
    const newExtracted = await extractFromFile(filepath);
    const oldExtracted = extractedByFile.get(filepath) || new Set();
    const isSameSet =
      oldExtracted.size === newExtracted.size && [...oldExtracted].every(x => newExtracted.has(x));

    if (!isSameSet || filepath.includes(themeDir)) {
      extractedByFile.set(filepath, newExtracted);
      await build();
      return true;
    } else return false;
  };

  return {
    build,
    update,
  };
}
