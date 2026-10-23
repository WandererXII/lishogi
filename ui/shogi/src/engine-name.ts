import { useJapanese } from 'common/common';
import { i18nFormat } from 'i18n';
import { isHandicap } from 'shogiops/handicaps';
import { initialSfen, parseSfen } from 'shogiops/sfen';
import type { Rules } from 'shogiops/types';

export const EngineCode = {
  YaneuraOu: 'yn',
  Fairy: 'fs',
} as const;

export type EngineCode = (typeof EngineCode)[keyof typeof EngineCode];

// modules/game/src/main/EngineConfig.scala
export function engineCode(rules: Rules, sfen: Sfen | undefined, level?: number): EngineCode {
  return rules === 'standard' &&
    (!level || level > 4) &&
    (!sfen ||
      initialSfen(rules) === sfen ||
      isHandicap({ sfen, rules }) ||
      isStandardMaterial(sfen))
    ? EngineCode.YaneuraOu
    : EngineCode.Fairy;
}

export function engineName(rules: Rules, sfen: Sfen | undefined, level?: number): string {
  const code = engineCode(rules, sfen, level);
  return engineNameFromCode(code, level);
}

export function engineNameFromCode(code?: EngineCode, level?: number): string {
  const name =
    code === EngineCode.Fairy ? 'Fairy Stockfish' : useJapanese() ? 'やねうら王' : 'YaneuraOu';
  if (level) return `${name} - ${i18nFormat('levelX', level)}`;
  else return name;
}

function isStandardMaterial(sfen: Sfen): boolean {
  const pos = parseSfen('standard', sfen, false);
  if (pos.isErr) return false;
  const board = pos.value.board;
  const hands = pos.value.hands.color('sente').combine(pos.value.hands.color('gote'));
  return (
    board.byRole('pawn').size() + board.byRole('tokin').size() + hands.get('pawn') <= 18 &&
    board.byRole('lance').size() + board.byRole('promotedlance').size() + hands.get('lance') <= 4 &&
    board.byRole('knight').size() + board.byRole('promotedknight').size() + hands.get('knight') <=
      4 &&
    board.byRole('silver').size() + board.byRole('promotedsilver').size() + hands.get('silver') <=
      4 &&
    board.byRole('gold').size() + hands.get('gold') <= 4 &&
    board.byRole('rook').size() + board.byRole('dragon').size() + hands.get('rook') <= 2 &&
    board.byRole('bishop').size() + board.byRole('horse').size() + hands.get('bishop') <= 2 &&
    board.byRole('king').size() == 2 &&
    board.byRole('king').intersect(board.byColor('sente')).size() === 1
  );
}

const ENGINE_NAME_TO_CODE: Record<string, EngineCode> = {
  yaneuraou: EngineCode.YaneuraOu,
  やねうら王: EngineCode.YaneuraOu,
  やねうらおう: EngineCode.YaneuraOu,
  fairy: EngineCode.Fairy,
  'fairy-stockfish': EngineCode.Fairy,
};

export function engineFromName(input: string): { code: EngineCode; level?: number } | undefined {
  if (!input) return;

  const lower = input.toLowerCase();

  const entry = Object.entries(ENGINE_NAME_TO_CODE).find(([name]) => lower.includes(name));
  if (!entry) return;

  const [_, code] = entry;

  const m = input.match(/(?:level|レベル)\s*([0-9]+)/i);
  const level = m ? Number(m[1]) : undefined;

  return { code, level };
}
