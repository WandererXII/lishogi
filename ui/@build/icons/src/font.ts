import * as fs from 'node:fs';
import { mkdir } from 'node:fs/promises';
import * as path from 'node:path';
import { writeIfChanged } from '@build/helpers/util';
import { FontAssetType, generateFonts } from '@twbs/fantasticon';

function toCamelCase(str: string): string {
  return str
    .split(/[_\-\s]+/)
    .map((part, i) =>
      i === 0 ? part.toLowerCase() : part.charAt(0).toUpperCase() + part.slice(1).toLowerCase(),
    )
    .join('');
}

function toKebabCase(str: string): string {
  return str
    .split(/[_\-\s]+/)
    .map(part => part.toLowerCase())
    .join('-');
}

function formatTsChar(cp: number): string {
  return `\\u${cp.toString(16).toUpperCase().padStart(4, '0')}`;
}

function formatScssChar(cp: number): string {
  return `\\${cp.toString(16).toUpperCase()}`;
}

export async function font(rootDir: string, outDir: string): Promise<void> {
  const assetsDir = path.join(import.meta.dirname, '../assets');
  const iconsDir = path.join(assetsDir, 'lishogi');
  const codepointsFile = path.join(import.meta.dirname, '../codepoints.json');

  const tsExportPath = path.join(rootDir, 'ui/common/src/icons.ts');
  const scssExportPath = path.join(rootDir, 'ui/common/css/abstract/_icons.scss');

  await mkdir(outDir, { recursive: true });

  let codepoints: Record<string, number> = {};
  if (fs.existsSync(codepointsFile)) {
    codepoints = JSON.parse(fs.readFileSync(codepointsFile, 'utf8'));
  }

  const result = await generateFonts({
    inputDir: iconsDir,
    outputDir: outDir,
    name: 'lishogi',
    fontTypes: [FontAssetType.WOFF2],
    assetTypes: [],
    codepoints,
    round: 0,
    normalize: true,
    descent: 0,
  });

  if (result.codepoints) {
    fs.writeFileSync(codepointsFile, JSON.stringify(result.codepoints, null, 2), 'utf8');

    const entries = Object.entries(result.codepoints).toSorted();

    // TS export
    const tsLines = ['// Auto-generated file\n', 'export const icons = {'];
    for (const [name, cp] of entries) {
      const key = toCamelCase(name);
      tsLines.push(`  ${key}: '${formatTsChar(cp)}',`);
    }
    tsLines.push('} as const;\n');
    const tsWritten = await writeIfChanged(tsExportPath, tsLines.join('\n'));
    if (tsWritten) console.log(`SCSS - Wrote ${tsExportPath}`);
    console.log(`TS - Wrote ${tsExportPath}`);

    // SCSS export
    const scssLines = ['// Auto-generated file\n'];
    for (const [name, cp] of entries) {
      const key = toKebabCase(name);
      scssLines.push(`$${key}: '${formatScssChar(cp)}';`);
    }
    scssLines.push('');

    const scssWritten = await writeIfChanged(scssExportPath, scssLines.join('\n'));
    if (scssWritten) console.log(`SCSS - Wrote ${scssExportPath}`);
    else console.log(`SCSS - File ${scssExportPath} unchanged`);
  }

  console.log('Fantasticon font build done.');
}
