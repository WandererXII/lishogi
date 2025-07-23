import type { Role } from 'shogiops/types';

export const tabs = ['outcomes', 'moves', 'times', 'analysis', 'opponents', 'custom'] as const;
export type Tab = (typeof tabs)[number];

export interface InsightOpts {
  username: string;
  usernameHash: string;
  endpoint: string;
  isBot: boolean;
  pref: any;
}

export interface InsightFilter {
  since: string; // days
  variant: VariantKey;
  color: Color | 'both';
  rated: 'yes' | 'no' | 'both';
  speeds: Speed[];
  computer: 'yes' | 'no' | 'both';
  custom: InsightCustom;
}
export type InsightFilterWithoutCustom = Omit<InsightFilter, 'custom'>;

type WithArray<T> = {
  [K in keyof T]: T[K] extends any[] ? T[K] : T[K][];
};
export type InsightFilterOptions = WithArray<InsightFilterWithoutCustom>;

export interface InsightCustom {
  type: 'game' | 'moves';
  x: string;
  y: string;
}

export interface InsightData {
  outcomes: OutcomeResult | undefined;
  moves: MovesResult | undefined;
  times: TimesResult | undefined;
  analysis: AnalysisResult | undefined;
  opponents: OpponentResult | undefined;
  custom: CustomResult | undefined;
}

export interface Result {
  nbOfGames: number;
}
export interface CustomResult extends Result {
  data?: {
    labels: string[];
    dataset: Record<string, Record<string, number>>;
  };
  error?: string;
}
export interface OutcomeResult extends Result {
  winrate: WinRate;
  winStatuses: CounterObj<StatusId>;
  lossStatuses: CounterObj<StatusId>;
}
export interface MovesResult extends Result {
  nbOfMoves: number;
  nbOfDrops: number;
  nbOfCaptures: number;
  nbOfPromotions: number;
  nbOfMovesByRole: CounterObj<Role>;
  nbOfDropsByRole: CounterObj<Role>;
  nbOfCapturesByRole: CounterObj<Role>;
  winrateByFirstMove: {
    sente: Record<string, WinRate>;
    gote: Record<string, WinRate>;
  };
  winrateByEarlyBishopExchange: {
    yes: WinRate;
    no: WinRate;
  };
}
export interface OpponentResult extends Result {
  avgOpponentRating: number;
  avgOpponentRatingDiff: number;
  winrateByMostPlayedOpponent: Record<string, WinRate>;
}
export interface TimesResult extends Result {
  totalTime: number;
  avgTimePerMoveAndDrop: number;
  avgTimePerMove: number;
  avgTimePerDrop: number;
  avgTimePerGame: number;
  sumOfTimesByMoveRole: PartialRecord<Role, Centis>;
  sumOfTimesByDropRole: PartialRecord<Role, Centis>;
  nbOfMovesByRole: CounterObj<Role>;
  nbOfDropsByRole: CounterObj<Role>;
}
export interface AnalysisResult extends Result {
  accuracy: Accuracy;
  accuracyByOutcome: [Accuracy, Accuracy, Accuracy];
  accuracyByOutcomeCount: [number, number, number];
  accuracyByMoveNumber: Record<number, Accuracy>;
  accuracyByMoveNumberCount: CounterObj<number>;
  accuracyByMoveRole: PartialRecord<Role, Accuracy>;
  accuracyByMoveRoleCount: PartialRecord<Role, number>;
  accuracyByDropRole: PartialRecord<Role, Accuracy>;
  accuracyByDropRoleCount: PartialRecord<Role, number>;
  accuracyByRole: PartialRecord<Role, Accuracy>; // total
  accuracyByRoleCount: PartialRecord<Role, number>;
}

export type CounterObj<TKey extends PropertyKey> = PartialRecord<TKey, number>;

type PartialRecord<TKey extends PropertyKey, TValue> = {
  [key in TKey]?: TValue;
};

export type WinRate = [number, number, number];

type Accuracy = number;
type Centis = number;

export const StatusObject = {
  checkmate: 30,
  resign: 31,
  stalemate: 32,
  timeout: 33,
  draw: 34,
  outoftime: 35,
  cheat: 36,
  noStart: 37,
  unknownFinish: 38,
  perpetualCheck: 40,
  impasse27: 41,
  royalsLost: 42,
  bareKing: 43,
  repetition: 44,
  specialVariantEnd: 45,
  illegalMove: 46,
} as const;
export type StatusId = (typeof StatusObject)[keyof typeof StatusObject];
export type StatusKey = keyof typeof StatusObject;

export const speeds: Speed[] = [
  'ultraBullet',
  'bullet',
  'blitz',
  'rapid',
  'classical',
  'correspondence',
];
export const variants: VariantKey[] = [
  'standard',
  'minishogi',
  'chushogi',
  'annanshogi',
  'kyotoshogi',
  'checkshogi',
];
