import { forsythToPiece, parseSfen } from 'shogiops/sfen';
import type { Piece } from 'shogiops/types';
import { opposite, parseSquareName, parseUsi } from 'shogiops/util';
import type { Position } from 'shogiops/variant/position';
import type { Level, Scenario, UsiWithColor } from './interfaces';

export function createScenario(usis: Usi[], color: Color = 'sente', switchColor = false): Scenario {
  return usis.map((usi, i) => {
    return {
      usi: usi,
      color: switchColor && i % 2 ? opposite(color) : color,
    };
  });
}

export function currentPosition(
  level: Level,
  usiCList: UsiWithColor[] = [],
  ignoreObstacles = false,
): Position {
  const shogi = parseSfen('standard', level.sfen, false).unwrap();
  const obstacles = level.obstacles;

  if (!ignoreObstacles && obstacles)
    for (const obstacle of obstacles) {
      shogi.board.set(parseSquareName(obstacle), { role: 'pawn', color: opposite(level.color) });
    }

  for (const uc of usiCList) {
    shogi.turn = uc.color;
    shogi.play(parseUsi(uc.usi)!);
  }
  return shogi;
}

export function toPiece(sfenPiece: string): Piece {
  return forsythToPiece('standard')(sfenPiece)!;
}

export function average(nums: number[]): number {
  const sum = nums.reduce((a, b) => a + b, 0);
  return sum / nums.length || 0;
}
