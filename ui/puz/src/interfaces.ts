import type { Piece } from 'shogiops/types';
import type { Clock } from './clock';
import type { Combo } from './combo';
import type CurrentPuzzle from './current';

export interface PuzPrefs {
  coords: 0 | 1 | 2 | 3;
  destination: boolean;
  dropDestination: boolean;
  moveEvent: number;
  highlightLastDests: boolean;
  highlightCheck: boolean;
  squareOverlay: boolean;
  resizeHandler: number;
}

export type UserMove = (orig: Key, dest: Key, prom: boolean) => void;
export type UserDrop = (piece: Piece, dest: Key) => void;

export interface Puzzle {
  id: string;
  sfen: string;
  line: string;
  rating: number;
  ambPromotions?: number[];
}

export interface Run {
  pov: Color;
  moves: number;
  errors: number;
  current: CurrentPuzzle;
  clock: Clock;
  history: Round[];
  combo: Combo;
  modifier: Modifier;
  endAt?: number;
}

interface Round {
  puzzle: Puzzle;
  win: boolean;
  millis: number;
}

interface Modifier {
  moveAt: number;
  malus?: TimeMod;
  bonus?: TimeMod;
}

export interface TimeMod {
  seconds: number;
  at: number;
}

export interface Config {
  clock: {
    initial: number;
    malus: number;
  };
  combo: {
    levels: [number, number][];
  };
}
