import { parseSfen } from 'shogiops/sfen';
import type { DropMove } from 'shogiops/types';
import { isDrop, makeUsi, parseUsi } from 'shogiops/util';
import { promote } from 'shogiops/variant/util';

export function toFairyKyotoFormat(sfen: Sfen, moves: string[]): string {
  // fairy expects something like this: p+nks+l/5/5/L+S1N+P/+LSK+NP
  // while we have this: pgkst/5/5/LB1NR/TSKGP
  const mappingBoard: Record<string, string> = {
    g: '+n',
    G: '+N',
    t: '+l',
    T: '+L',
    b: '+s',
    B: '+S',
    r: '+p',
    R: '+P',
  };
  // fairy wants PNLS
  // we have PGTS
  const mappingHand: Record<string, string> = {
    g: 'n',
    G: 'N',
    t: 'l',
    T: 'L',
  };
  function transformString(sfen: string, mapping: Record<string, string>) {
    return sfen
      .split('')
      .map(c => mapping[c] || c)
      .join('');
  }

  const uMoves: string[] = [];
  const pos = parseSfen('kyotoshogi', sfen, false).unwrap();
  moves.forEach(usi => {
    const move = parseUsi(usi)!;
    // G*3b -> +N*3b
    if (isDrop(move)) {
      const roleChar = usi[0];
      const uUsi = (mappingBoard[roleChar] || roleChar) + usi.slice(1);
      uMoves.push(uUsi);
    }
    // 5e4d+ -> 5e4d-, if necessary
    else if (move.promotion) {
      const roleChar = pos.board.getRole(move.from)![0];
      const fairyUsi = mappingBoard[roleChar]?.includes('+') ? `${usi.slice(0, -1)}-` : usi;

      uMoves.push(fairyUsi);
    } else uMoves.push(usi);
    pos.play(move);
  });

  const splitSfen = sfen.split(' ');
  return `position sfen ${transformString(splitSfen[0], mappingBoard)} ${splitSfen[1] || 'b'} ${transformString(
    splitSfen[2] || '-',
    mappingHand,
  )} moves ${uMoves.join(' ')}`;
}

export function fromFairyKyotoFormat(moves: string[]): Usi[] {
  return moves.map(usi => {
    // +N*3b -> G*3b
    if (usi[0] === '+') {
      const dropUnpromoted = parseUsi(usi.slice(1)) as DropMove;
      const promotedRole = promote('kyotoshogi')(dropUnpromoted.role)!;
      return makeUsi({ role: promotedRole, to: dropUnpromoted.to });
    }
    // 5e4d- -> 5e4d+
    else if (usi.includes('-')) return `${usi.slice(0, -1)}+`;
    else return usi;
  });
}

export function toFairyDobutsuFormat(sfen: string, moves: string[]): string {
  const mapping: Record<string, string> = {
    r: 'g',
    R: 'G',
    k: 'l',
    K: 'L',
    b: 'e',
    B: 'E',
    p: 'c',
    P: 'C',
  };

  function mapSfenBoard(board: string): string {
    let out = '';
    for (let i = 0; i < board.length; i++) {
      const c = board[i];
      if (c === '+' && i + 1 < board.length) {
        const next = board[++i];
        out += `+${mapping[next] || next}`;
      } else {
        out += mapping[c] || c;
      }
    }
    return out;
  }

  function mapMove(usi: string): string {
    // drops - P*2b -> C*2b
    if (usi[1] === '*') {
      const role = usi[0];
      return (mapping[role] || role) + usi.slice(1);
    }
    return usi;
  }

  const split = sfen.split(' ');
  const board = mapSfenBoard(split[0]);
  const turn = split[1] || 'b';
  const hands = split[2] || '-';

  const uMoves = moves.map(mapMove);

  return `position sfen ${board} ${turn} ${hands} moves ${uMoves.join(' ')}`;
}

export function fromFairyDobutsuFormat(moves: string[]): Usi[] {
  const reverse: Record<string, string> = {
    g: 'r',
    G: 'R',
    l: 'k',
    L: 'K',
    e: 'b',
    E: 'B',
    c: 'p',
    C: 'P',
  };

  return moves.map(usi => {
    // drops - C*2b -> P*2b
    if (usi[1] === '*') {
      const role = usi[0];
      return (reverse[role] || role) + usi.slice(1);
    }

    return usi;
  });
}
