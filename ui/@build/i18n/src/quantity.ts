// Originally from https://github.com/lichess-org/lila/blob/32386c7bfd7bdd617d30a2af2838ec3c687edb6d/ui/.build/src/i18n.ts
// Must match CLDR plural rules (Intl.PluralRules) for whole numbers, since Crowdin creates
// plural slots from CLDR. Keep in sync with modules/i18n/src/main/I18nQuantity.scala.
// Only covers languages in constants.ts, anything else gets the default rule.
export function quantity(lang: string): string {
  switch (lang.split('-')[0]) {
    case 'pt':
      return lang === 'pt-BR' ? `o=>o<=1?"one":"other"` : `o=>o==1?'one':'other'`;
    case 'fr':
    case 'fa':
    case 'hi':
      return `o=>o<=1?"one":"other"`; // french
    case 'tl': // filipino
      return `o=>{const t=o%10;return o<=3||!(4==t||6==t||9==t)?"one":"other"}`;
    case 'cs': // czech
      return `o=>1==o?"one":o>=2&&o<=4?"few":"other"`;
    case 'ru':
    case 'uk':
    case 'be': // east slavic
      return `o=>{const e=o%100,t=o%10;return 1==t&&11!=e?"one":t>=2&&t<=4&&!(e>=12&&e<=14)?"few":"many"}`;
    case 'sr': // serbian
      return `o=>{const e=o%100,t=o%10;return 1==t&&11!=e?"one":t>=2&&t<=4&&!(e>=12&&e<=14)?"few":"other"}`;
    case 'lt': // lithuanian
      return `o=>{const e=o%100,t=o%10;return 1!=t||e>=11&&e<=19?t>=2&&t<=9&&!(e>=11&&e<=19)?"few":"other":"one"}`;
    case 'pl': // polish
      return `o=>{const e=o%100,t=o%10;return 1==o?"one":t>=2&&t<=4&&!(e>=12&&e<=14)?"few":"many"}`;
    case 'ro': // romanian
      return `o=>{const e=o%100;return 1==o?"one":0==o||e>=1&&e<=19?"few":"other"}`;
    case 'ar': // arabic
      return `o=>{const e=o%100;return 0==o?"zero":1==o?"one":2==o?"two":e>=3&&e<=10?"few":e>=11&&e<=99?"many":"other"}`;
    case 'he': // hebrew
      return `o=>1==o?"one":2==o?"two":"other"`;
    case 'id':
    case 'ja':
    case 'ko':
    case 'th':
    case 'vi':
    case 'zh':
      return `o=>"other"`;
    default:
      return `o=>o==1?'one':'other'`;
  }
}
