import type { Shogi } from 'shogiops/position/rules/shogi';
import { parseSfen } from 'shogiops/sfen';
import { parseUsi } from 'shogiops/util';
import type { Puzzle } from './interfaces';
import { getNow } from './util';

export default class CurrentPuzzle {
  line: Usi[];
  startAt: number;
  moveIndex = 0;
  pov: Color;

  constructor(
    readonly index: number,
    readonly puzzle: Puzzle,
  ) {
    this.line = puzzle.line.split(' ');
    this.pov = parseSfen('standard', puzzle.sfen, false).unwrap().turn;
    this.startAt = getNow();
  }

  position = (): Shogi => {
    let pos = parseSfen('standard', this.puzzle.sfen, false).unwrap() as Shogi;
    this.line.slice(0, this.moveIndex).forEach(usi => {
      pos = pos.play(parseUsi(usi)!);
    });
    return pos;
  };

  expectedMove = (): string => this.line[this.moveIndex];

  lastMove = (): string => this.line[this.moveIndex - 1];

  isOver = (): boolean => this.moveIndex >= this.line.length - 1;
}
