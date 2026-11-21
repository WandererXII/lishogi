import { writeFile } from 'node:fs/promises';
import * as path from 'node:path';
import { prefix, signature, themes } from '../constants.js';
import { defaultTheme, type Theme, type ThemeRecord } from '../types.js';

export async function generateThemeVariables(
  rootDir: string,
  themeVars: ThemeRecord,
  extracted: string[],
  outDir: string,
): Promise<void> {
  warnMixDuplicates(extracted);

  for (const theme of themes) {
    const defaultThemeByCategory: Theme = theme.endsWith('-new')
      ? (`${defaultTheme}-new` as Theme)
      : defaultTheme;
    let output = `${signature} ${path.relative(rootDir, import.meta.filename)}

@use 'sass:color';
@use '../../abstract/icons';
@use '../util' as *;
@use '../${defaultThemeByCategory}' as *;
${theme !== defaultThemeByCategory ? `@use '../${theme}' as *;` : ''}
`;

    const sel = theme === defaultThemeByCategory ? 'html' : `html.${theme}`;
    output += `\n${sel} {\n`;

    const vars = themeVars[theme];
    const varKeys = Object.keys(vars).sort();
    let varOutput = '';

    varKeys.forEach(name => {
      const value = vars[name];
      if (
        theme !== defaultThemeByCategory &&
        !value.includes('c-') &&
        themeVars[defaultThemeByCategory][name] === value
      )
        console.warn(`Redundant variable repetition: $${name}: ${value}in ${theme}`);
      varOutput += cssVariable(name, value);
    });

    extracted.forEach(name => {
      const v = colorFunction(name, varKeys);
      if (v) varOutput += cssVariable(name, v);
    });

    output += varOutput;
    output += '}\n';

    if (theme === 'light') {
      output += '\n';
      output += `@media (prefers-color-scheme: ${theme}) {\n`;
      output += '  html.system {\n';
      output += varOutput;
      output += '  }\n';
      output += '}\n';
    }

    await writeFile(path.join(outDir, `_${theme}-vars.scss`), output);
  }
}

function cssVariable(name: string, value: string): string {
  if (value.startsWith('"') || value.startsWith("'")) return `  --${name}: ${value};\n`;
  return `  --${name}: #{${value}};\n`;
}

function colorFunction(variable: string, themeKeys: string[]): string | undefined {
  function themifyColor(color: string): string {
    if (['black', 'white'].includes(color)) return color;
    else return `$c-${color}`;
  }

  const name = variable.substring(prefix.length);
  const parts = name.split('_');
  const length = parts.length;
  const percentagePart = parts[length - 1];
  const percentage = Number.parseInt(`${percentagePart.replace('neg', '-')}`);
  const func = parts[length - 2];
  const colors = parts.slice(0, -2).map(p => themifyColor(p));

  if (!colors.some(c => themeKeys.includes(c.split('$')[1]))) {
    return;
  }

  if (func === 'mix') {
    return `color.mix(${[colors[0], colors[1], `${percentage}%`].join(',')})`;
  } else {
    const value = func.endsWith('alpha') ? (percentage / 100).toString() : `${percentage}%`;
    return `color.${func.startsWith('c-') ? 'change' : 'adjust'}(${colors[0]},$${func.replace('c-', '')}:${value})`;
  }
}

// $m-accent_bg-box_mix_10 and $m-bg-box_accent_mix_90 produce the same color
function warnMixDuplicates(extracted: string[]): void {
  const seen = new Map<string, string>();
  for (const variable of extracted) {
    const parts = variable.substring(prefix.length).split('_');
    if (parts.length !== 4 || parts[2] !== 'mix') continue;
    const percentage = Number.parseInt(parts[3].replace('neg', '-'));
    const normalized = normalizeMixVariable(parts.slice(0, 2), percentage);
    const prev = seen.get(normalized);
    if (prev) console.warn(`Found duplicates: ${prev} and ${variable}`);
    else seen.set(normalized, variable);
  }
}

function normalizeMixVariable(colors: string[], percentage: number): string {
  const sortedCols = colors.slice().sort();
  const sortedPerc = sortedCols[0] === colors[0] ? percentage : 100 - percentage;
  return `${prefix}${[...sortedCols, 'mix', sortedPerc].join('_')}`;
}
