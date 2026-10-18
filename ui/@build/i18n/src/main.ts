import type { WatchEventType } from 'node:fs';
import fs from 'node:fs/promises';
import path from 'node:path';
import { workspaceBuildConductor } from '@build/helpers/workspace-builder';
import { getPackageFromPath, type Project } from '@build/helpers/workspace-packages';
import { defaultLang, otherLangs } from './constants.js';
import { extractI18nKeysFromPackage } from './parser/extractor.js';
import { quantity } from './quantity.js';
import { timeago } from './timeago.js';
import { isValidJs } from './util.js';
import { createXmlManager } from './xml-manager.js';

await workspaceBuildConductor('i18n', async (rootDir: string, pkgs: Project[], outDir: string) => {
  const xmlManager = await createXmlManager(rootDir);
  await xmlManager.init();

  const separatePackages = ['learn', 'insights', 'puzzle'];
  await Promise.all(
    [...separatePackages, 'core'].map(async p =>
      fs.mkdir(path.join(outDir, p), { recursive: true }),
    ),
  );

  const i18nPackagesMap = new Map<string, Set<string>>();

  for (const pkg of pkgs) {
    const extracts = await extractI18nKeysFromPackage(pkg);
    i18nPackagesMap.set(pkg.manifest.name, extracts);
    if (process.argv.includes('--debug')) console.log(`${pkg.manifest.name}: ${extracts.size}`);
  }

  async function bundleI18ns() {
    const source = xmlManager.source();
    const dest = xmlManager.dest();

    const coreKeys = new Set<string>();
    for (const [name, keys] of i18nPackagesMap) {
      if (!separatePackages.includes(name)) {
        for (const key of keys) {
          coreKeys.add(key);
        }
      }
    }

    const keysByOuputPackage = new Map<string, Set<string>>();
    for (const name of separatePackages) {
      const keys = new Set<string>();
      for (const key of i18nPackagesMap.get(name)!) {
        if (!coreKeys.has(key)) keys.add(key);
      }
      keysByOuputPackage.set(name, keys);
    }
    keysByOuputPackage.set('core', coreKeys);

    for (const lang of [...otherLangs, defaultLang]) {
      const i18n = lang === defaultLang ? source : dest.get(lang)!;
      if (!i18n) console.warn(`No i18n for ${lang}`);

      for (const [name, keys] of keysByOuputPackage) {
        const obj: Record<string, string> = {};

        for (const key of Array.from(keys).sort()) {
          const value = i18n[key] || source[key];

          if (typeof value === 'string') obj[key] = value;
          else if (typeof value === 'object') {
            for (const [pKey, pValue] of Object.entries(value)) {
              obj[`${key}|${pKey}`] = pValue;
            }
          } else console.warn(`No valid value found for: ${key} (${lang})`);
        }

        const code =
          name === 'core'
            ? `window.lishogi.i18n=${JSON.stringify(obj)};window.lishogi.quantity=${quantity(lang)};${timeago(lang)}`
            : `Object.assign(window.lishogi.i18n||{}, ${JSON.stringify(obj)});`;

        if (isValidJs(code)) await fs.writeFile(path.join(outDir, name, `${lang}.js`), code);
        else throw new Error(`Invalid code: ${name}, ${lang}`);
      }
    }
    console.log('Bundled all languages');
  }

  return {
    all: async () => {
      await bundleI18ns();
    },
    onChange: async (_event: WatchEventType, filepath: string | null) => {
      if (filepath === null) return;

      await xmlManager.update(filepath);
      const pkg = getPackageFromPath(pkgs, filepath);
      if (pkg) {
        const oldKeys = i18nPackagesMap.get(pkg.manifest.name)!;
        const newKeys = await extractI18nKeysFromPackage(pkg);
        const isSame = oldKeys.size === newKeys.size && [...oldKeys].every(el => newKeys.has(el));
        if (!isSame) {
          console.log(`Change detected in: ${filepath}`);
          i18nPackagesMap.set(pkg.manifest.name, newKeys);
          await bundleI18ns();
        }
      } else {
        console.log(`Change detected in: ${filepath}`);
        await bundleI18ns();
      }
    },
    stop: async () => {},
    watchPaths: ['src', `${rootDir}/translation`],
  };
});
