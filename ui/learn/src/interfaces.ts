import type { DrawShape, SquareHighlight } from 'shogiground/draw';

export type Score = 0 | 1 | 2 | 3;

type StageKey = string;
type StageId = number;
type LevelId = number;
type CategoryKey = string;

export interface UsiWithColor {
  usi: Usi;
  color: Color;
}

export type Shape = DrawShape | SquareHighlight;

export type VmEvaluation<T> = (level: Level, usiCList: UsiWithColor[]) => T;

export type Assertion = VmEvaluation<boolean>;

export type Scenario = UsiWithColor[];

export interface Level {
  id: LevelId;
  goal: string; // i18n string displayed on the right
  sfen: Sfen; // starting position of the level
  color: Color; // user's color
  nbMoves: number; // number of moves it takes to get perfect score

  success: Assertion;
  failure?: Assertion;

  obstacles?: Key[]; // placed on the board, can be 'captured', offerIllegalDests by default for king collisions
  scenario?: UsiWithColor[];

  drawShapes?: VmEvaluation<DrawShape[]>;
  squareHighlights?: VmEvaluation<SquareHighlight[]>;

  text?: string; // helping text displayed on the board
  nextButton?: boolean; // wait for user to click next - allows user to review the solution
  offerIllegalDests?: boolean;
  showFailureMove?: 'random' | 'capture' | 'unprotected' | VmEvaluation<Usi | undefined>; // played after failure
}

export type IncompleteLevel = Omit<Level, 'id' | 'color'> & Partial<Pick<Level, 'color'>>;

export interface Stage {
  id: StageId;
  key: StageKey;
  title: string; // title to the right of the board
  subtitle: string; // below title
  intro: string; // overlay when stage starts
  complete: string; // overlay after stage is completed
  levels: Level[];
}

export type IncompleteStage = Omit<Stage, 'id'>;

export interface Category {
  key: CategoryKey;
  name: string;
  stages: Stage[];
}

type StageState = 'init' | 'running' | 'completed' | 'end';
type LevelState = 'play' | 'completed' | 'fail';

export interface Vm {
  category: Category;
  sideCategory: CategoryKey;
  stage: Stage;
  stageState: StageState;
  level: Level;
  levelState: LevelState;
  usiCList: UsiWithColor[];
  score?: Score;
}

export interface LearnOpts {
  data?: LearnProgress;
  pref: any;
}

export interface LearnProgress {
  _id?: string;
  stages: Record<string, ProgressScore>;
}

interface ProgressScore {
  scores: Score[];
}
