import { i18n } from './i18n';

export function i18nVariant(key: VariantKey): string;
export function i18nVariant(key: string): string | undefined;
export function i18nVariant(key: string): string | undefined {
  switch (key) {
    case 'standard':
      return i18n('standard');
    case 'minishogi':
      return i18n('minishogi');
    case 'chushogi':
      return i18n('chushogi');
    case 'annanshogi':
      return i18n('annanshogi');
    case 'kyotoshogi':
      return i18n('kyotoshogi');
    case 'checkshogi':
      return i18n('checkshogi');
    case 'dobutsu':
      return i18n('dobutsu');
    default:
      return undefined;
  }
}
