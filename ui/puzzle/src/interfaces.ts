import { CevalCtrl, NodeEvals } from 'ceval';
import { Prop } from 'common/common';
import { Deferred } from 'common/defer';
import { StoredBooleanProp } from 'common/storage';
import { Api as SgApi } from 'shogiground/api';
import { Config as SgConfig } from 'shogiground/config';
import { Move, Outcome, Piece } from 'shogiops/types';
import { Shogi } from 'shogiops/variant/shogi';
import { TreeWrapper } from 'tree';
import PuzzleSession from './session';

export type Redraw = () => void;

export interface KeyboardController {
  vm: Vm;
  redraw: Redraw;
  userJump(path: Tree.Path): void;
  getCeval(): CevalCtrl;
  toggleCeval(): void;
  toggleThreatMode(): void;
  playBestMove(): void;
}

export type ThemeKey = string;
export interface AllThemes {
  dynamic: ThemeKey[];
  static: Set<ThemeKey>;
}

export type PuzzleDifficulty = 'easiest' | 'easier' | 'normal' | 'harder' | 'hardest';

export interface Controller extends KeyboardController {
  nextNodeBest(): string | undefined;
  disableThreatMode?: Prop<boolean>;
  outcome(): Outcome | undefined;
  mandatoryCeval?: Prop<boolean>;
  showEvalGauge: Prop<boolean>;
  currentEvals(): NodeEvals;
  ongoing: boolean;
  playUsi(usi: string): void;
  playUsiList(usiList: string[]): void;
  getOrientation(): Color;
  threatMode: Prop<boolean>;
  getNode(): Tree.Node;
  position(): Shogi;
  showComputer(): boolean;
  trans: Trans;
  getData(): PuzzleData;
  data: PuzzleOpts;
  getTree(): TreeWrapper;
  shogiground: SgApi;
  makeSgOpts(): SgConfig;
  viewSolution(): void;
  nextPuzzle(): void;
  vote(v: boolean): void;
  voteTheme(theme: ThemeKey, v: boolean): void;
  pref: PuzzlePrefs;
  difficulty?: PuzzleDifficulty;
  userMove(orig: Key, dest: Key, prom: boolean): void;
  userDrop(piece: Piece, dest: Key, prom: boolean): void;
  autoNext: StoredBooleanProp;
  autoNexting: () => boolean;
  session: PuzzleSession;
  allThemes?: AllThemes;

  path?: Tree.Path;
  autoScrollRequested?: boolean;
}

export interface Vm {
  path: Tree.Path;
  nodeList: Tree.Node[];
  node: Tree.Node;
  mainline: Tree.Node[];
  pov: Color;
  mode: 'play' | 'view' | 'try';
  round?: PuzzleRound;
  next: Deferred<PuzzleData>;
  resultSent: boolean;
  lastFeedback: 'init' | 'fail' | 'win' | 'good' | 'retry';
  initialPath: Tree.Path;
  initialNode: Tree.Node;
  canViewSolution: boolean;
  autoScrollRequested: boolean;
  autoScrollNow: boolean;
  voteDisabled?: boolean;
  showComputer(): boolean;
  showAutoShapes(): boolean;
}

export interface PuzzleOpts {
  pref: PuzzlePrefs;
  data: PuzzleData;
  i18n: { [key: string]: string | undefined };
  difficulty?: PuzzleDifficulty;
  themes?: {
    dynamic: string;
    static: string;
  };
}

export interface PuzzlePrefs {
  coords: 0 | 1 | 2;
  destination: boolean;
  dropDestination: boolean;
  moveEvent: number;
  highlightLastDests: boolean;
  highlightCheck: boolean;
  squareOverlay: boolean;
  animation: {
    duration: number;
  };
  blindfold: boolean;
  notation: number;
}

export interface Theme {
  key: ThemeKey;
  name: string;
  desc: string;
  chapter?: string;
}

export interface PuzzleData {
  puzzle: Puzzle;
  theme: Theme;
  game: PuzzleGame;
  user: PuzzleUser | undefined;
  replay?: PuzzleReplay;
}

export interface PuzzleReplay {
  i: number;
  of: number;
  days: number;
}

// todo - separate outside sources and lishogi games
export interface PuzzleGame {
  // From games
  id?: string;
  perf?: {
    icon: string;
    name: string;
  };
  rated?: boolean;
  players?: [PuzzlePlayer, PuzzlePlayer];
  moves?: string;
  clock?: string;
  // From the outside
  sfen?: string;
  author?: string;
  description?: string;
}

export interface PuzzlePlayer {
  userId: string;
  name: string;
  title?: string;
  ai?: number;
  color: Color;
}

export interface PuzzleUser {
  id: string;
  rating: number;
  provisional?: boolean;
}

export interface Puzzle {
  id: string;
  solution: Usi[];
  rating: number;
  plays: number;
  initialPly: number;
  themes: ThemeKey[];
}

export interface PuzzleResult {
  round?: PuzzleRound;
  next: PuzzleData;
  replayComplete?: boolean;
}

export interface RoundThemes {
  [key: string]: boolean;
}

export interface PuzzleRound {
  win: boolean;
  ratingDiff: number;
  themes?: RoundThemes;
}

export interface MoveTest {
  move: Move;
  sfen: Sfen;
  path: Tree.Path;
}
