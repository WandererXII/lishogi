import type { MaybeVNodes } from 'common/snabbdom';
import { flagImage } from 'common/snabbdom';
import { i18n, i18nFormat } from 'i18n';
import { h } from 'snabbdom';
import { engineFromName, engineNameFromCode } from './engine-name';
import { extractRank, type Rank, rankTag } from './rank';

export interface UsernameData {
  username?: string;
  rank?: Rank;
  title?: string;
  engineLvl?: number;
  countryCode?: string;
  patron?: boolean;
}

export function usernameVNodes(data: UsernameData): MaybeVNodes {
  const isBot = data.title?.toLowerCase() === 'bot';
  return [
    !isBot && data.rank ? rankTag(data.rank) : undefined,
    data.engineLvl ? h('span.engine-lvl', `${i18nFormat('levelX', data.engineLvl)} `) : undefined,
    isBot ? h('span.bot-tag', 'BOT ') : undefined,
    h(`span.name${!data.username ? '.anon' : ''}`, data.username || i18n('anonymousUser')),
    data.countryCode ? flagImage(data.countryCode) : undefined,
  ];
}

export function usernameDataFromName(name: string | undefined): UsernameData {
  const engine = name ? engineFromName(name) : undefined;
  if (engine)
    return {
      username: engineNameFromCode(engine.code),
      engineLvl: engine.level,
    };
  else {
    const isBot = name?.toLowerCase().startsWith('bot ');
    const rank = !isBot ? extractRank(name) : undefined;
    const nameWithoutPrefix =
      (rank || isBot) && name ? name.slice(name.indexOf(' ') + 1 || 0) : name;

    return {
      username: nameWithoutPrefix,
      rank: rank,
      title: isBot ? 'BOT' : undefined,
    };
  }
}
