import { i18n } from './i18n';
import { i18nVariant } from './variant';

export function i18nPerf(key: Perf): string;
export function i18nPerf(key: string): string | undefined;
export function i18nPerf(str: string): string | undefined {
  switch (str) {
    case 'realTime':
      return i18n('shogi');
    case 'correspondence':
      return i18n('correspondence');
    default:
      return i18nVariant(str);
  }
}
