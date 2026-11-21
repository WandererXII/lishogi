import fs from 'node:fs/promises';
import path from 'node:path';
import { writeIfChanged } from '@build/helpers/util';
import { otherLangs } from './constants.js';
import { parseXmls } from './parser/xml.js';
import type { I18nObj } from './types.js';

interface TranslationManager {
  init: () => Promise<void>;
  update: (filepaths: string[]) => Promise<void>;
  source: () => I18nObj;
  dest: () => Map<string, I18nObj>;
}

export async function createXmlManager(rootDir: string): Promise<TranslationManager> {
  const translationDir = path.join(rootDir, 'translation');
  const sourceDir = path.join(translationDir, 'source');
  const destDir = path.join(translationDir, 'dest');

  let sourceI18n: I18nObj = {};
  const destI18n = new Map<string, I18nObj>();

  async function initSources(): Promise<void> {
    const sourceFiles = (await fs.readdir(sourceDir))
      .filter(file => file.endsWith('.xml'))
      .sort((a, b) => (a === 'site.xml' ? -1 : b === 'site.xml' ? 1 : 0));
    sourceI18n = await parseXmls(
      sourceFiles.map(sf => {
        return { name: categoryName(sf), path: path.join(sourceDir, sf) };
      }),
    );
    console.log('Parsed translation source directory');

    const written = await writeIfChanged(
      path.join(rootDir, '/ui/@types/lishogi/i18n.d.ts'),
      dtsFile(rootDir, sourceI18n),
    );
    if (written) console.log('Generated i18n dts file');
    else console.log('i18n.d.ts aready up to date');
  }

  async function initDests(): Promise<void> {
    destI18n.clear();
    const categoryDirs = await fs.readdir(destDir);
    for (const lang of otherLangs) {
      const sources = categoryDirs.map(cd => {
        return { name: categoryName(cd), path: path.join(destDir, cd, `${lang}.xml`) };
      });
      const existing = await Promise.all(
        sources.map(s =>
          fs.access(s.path).then(
            () => true,
            () => false,
          ),
        ),
      );
      const missing = sources.filter((_, i) => !existing[i]);
      if (missing.length)
        console.warn(
          `Missing ${lang} translation files: ${missing.map(s => path.relative(rootDir, s.path)).join(', ')}`,
        );
      const i18n = await parseXmls(sources.filter((_, i) => existing[i]));
      destI18n.set(lang, i18n);
    }
    console.log('Parsed translation dest directory');
  }

  return {
    init: async () => {
      await Promise.all([initSources(), initDests()]);
    },
    update: async filepaths => {
      if (filepaths.some(fp => fp.startsWith(sourceDir))) await initSources();
      if (filepaths.some(fp => fp.startsWith(destDir))) await initDests();
    },
    source: () => sourceI18n,
    dest: () => destI18n,
  };
}

function dtsFile(rootDir: string, sourceI18n: I18nObj): string {
  const plurals = Object.entries(sourceI18n)
    .filter(([, value]) => typeof value === 'object')
    .map(([key]) => key);

  const interpolates = Object.entries(sourceI18n)
    .filter(
      ([, value]) => typeof value === 'string' && (value.includes('%s') || value.includes('%1$s')),
    )
    .map(([key]) => key);

  const takenKeys = new Set([...plurals, ...interpolates]);
  const basic = Object.entries(sourceI18n)
    .filter(([key]) => !takenKeys.has(key))
    .map(([key]) => key);

  return `// Generated with ${path.relative(rootDir, import.meta.filename)}

// biome-ignore format: Auto generated
declare global {
  type I18nKeyPlural =
${plurals.map(k => `    | '${k}'`).join('\n')};

  type I18nKeyInterpolate =
${interpolates.map(k => `    | '${k}'`).join('\n')};

  type I18nKeyBasic =
${basic.map(k => `    | '${k}'`).join('\n')};
}

export {};
`;
}

function categoryName(name: string): string {
  return path.basename(name, '.xml'); // if file is given
}
