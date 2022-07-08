export type ColorChoice = Color | 'random';

export type TimeControl = 'untimed' | 'thirtySeconds';

export type Mode = 'findSquare' | 'nameSquare';

export type InputMethod = 'text' | 'buttons';

interface SenteGoteScores {
  sente: number[];
  gote: number[];
}

export interface ModeScores {
  findSquare: SenteGoteScores;
  nameSquare: SenteGoteScores;
}

export interface CoordinateTrainerConfig {
  i18n: I18nDict;
  is3d: boolean;
  resizePref: number;
  scores: ModeScores;
}

export type CoordModifier = 'next' | 'current';

export type Redraw = () => void;
