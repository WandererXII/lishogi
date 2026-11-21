import { readFile } from 'node:fs/promises';
import { XMLParser } from 'fast-xml-parser';
import type { I18nObj, PluralCategory, XmlSource } from '../types.js';

interface StringResource {
  '@_name': string;
  '#text'?: unknown;
}

interface PluralItem {
  '@_quantity': PluralCategory;
  '#text'?: unknown;
}

interface PluralsResource {
  '@_name': string;
  item: PluralItem[];
}

interface Resources {
  string?: StringResource[];
  plurals?: PluralsResource[];
}

interface ParsedResources {
  resources: Resources;
}

const parser = new XMLParser({
  ignoreAttributes: false,
  attributeNamePrefix: '@_',
  textNodeName: '#text',
  // keep values verbatim: no number/boolean coercion, no trimming of deliberate whitespace
  parseTagValue: false,
  trimValues: false,
});

function escapeText(value: unknown): string {
  const str = value === undefined || value === null ? '' : String(value);
  return str.replaceAll('\\"', '"').replaceAll("\\'", "'");
}

export async function parseXmls(sources: XmlSource[]): Promise<I18nObj> {
  const keysObjects = await Promise.all(sources.map(s => parseXml(s)));
  return keysObjects.reduce((acc, obj) => ({ ...acc, ...obj }), {});
}

async function parseXml(source: XmlSource): Promise<I18nObj> {
  const result: I18nObj = {};
  const xmlContent = await readFile(source.path, 'utf-8');
  const prefix = source.name === 'site' ? '' : `${source.name}:`;
  const parsed = parser.parse(xmlContent) as ParsedResources;

  if (parsed.resources.string) {
    const strings = Array.isArray(parsed.resources.string)
      ? parsed.resources.string
      : [parsed.resources.string];

    strings.forEach(str => {
      const key = `${prefix}${str['@_name']}`;
      result[key] = escapeText(str['#text']);
    });
  }

  if (parsed.resources.plurals) {
    const plurals = Array.isArray(parsed.resources.plurals)
      ? parsed.resources.plurals
      : [parsed.resources.plurals];

    plurals.forEach(plural => {
      const baseKey = `${prefix}${plural['@_name']}`;
      const items = Array.isArray(plural.item) ? plural.item : [plural.item];

      result[baseKey] = items.reduce(
        (acc, item) => {
          acc[item['@_quantity']] = escapeText(item['#text']);
          return acc;
        },
        {} as Partial<Record<PluralCategory, string>>,
      );
    });
  }

  return result;
}
