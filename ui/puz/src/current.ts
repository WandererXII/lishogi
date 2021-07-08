import { Shogi } from 'shogiops';
import { parseLishogiUsi } from 'shogiops/compat';
import { parseFen } from 'shogiops/fen';
import { Puzzle } from './interfaces';
import { getNow } from './util';

export default class CurrentPuzzle {
  line: Usi[];
  startAt: number;
  moveIndex: number = 0;
  pov: Color;

  constructor(readonly index: number, readonly puzzle: Puzzle) {
    this.line = puzzle.line.split(' ');
    this.pov = parseFen(puzzle.fen).unwrap().turn;
    this.startAt = getNow();
  }

  position = (): Shogi => {
    const pos = Shogi.fromSetup(parseFen(this.puzzle.fen).unwrap(), false).unwrap();
    this.line.slice(0, this.moveIndex).forEach(usi => pos.play(parseLishogiUsi(usi)!));
    return pos;
  };

  expectedMove = () => this.line[this.moveIndex];

  lastMove = () => this.line[this.moveIndex - 1];

  isOver = () => this.moveIndex >= this.line.length - 1;
}
