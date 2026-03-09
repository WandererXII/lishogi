import { isHandicap } from 'shogiops/handicaps';
import { parseSfen } from 'shogiops/sfen';
import type { Rules } from 'shogiops/types';
import { promotionZone } from 'shogiops/variant/util';

interface ImpasseInfo {
  king: boolean;
  nbOfPieces: number;
  pieceValue: number;
  check?: boolean;
}
interface ImpasseInfoByColor {
  sente: ImpasseInfo;
  gote: ImpasseInfo;
}

export const impasseNecessarySenteScore = 28;
export const impasseNecessaryGoteScore = 27;
export const impasseNecessaryEnteredPieces = 10;

export function impasseInfo(
  rules: Rules,
  sfen: Sfen,
  initialSfen?: Sfen,
): ImpasseInfoByColor | undefined {
  if (!['standard', 'annanshogi', 'checkshogi'].includes(rules)) return;

  const shogi = parseSfen(rules, sfen, false);

  if (shogi.isErr) return;

  const pointOffset = initialSfen ? pointOffsetFromSfen(rules, initialSfen) : 0;
  const board = shogi.value.board;
  const sentePromotion = promotionZone(rules)('sente').intersect(board.color('sente'));
  const gotePromotion = promotionZone(rules)('gote').intersect(board.color('gote'));
  const allMajorPieces = board
    .role('bishop')
    .union(board.role('rook'))
    .union(board.role('horse'))
    .union(board.role('dragon'));

  const senteKing: boolean = !sentePromotion.intersect(board.role('king')).isEmpty();
  const goteKing: boolean = !gotePromotion.intersect(board.role('king')).isEmpty();

  const senteNumberOfPieces: number = sentePromotion.diff(board.role('king')).size();
  const goteNumberOfPieces: number = gotePromotion.diff(board.role('king')).size();

  const senteImpasseValue =
    senteNumberOfPieces +
    allMajorPieces.intersect(sentePromotion).size() * 4 +
    shogi.value.hands.color('sente').count() +
    (shogi.value.hands.color('sente').get('bishop') +
      shogi.value.hands.color('sente').get('rook')) *
      4;

  const goteImpasseValue =
    pointOffset +
    goteNumberOfPieces +
    allMajorPieces.intersect(gotePromotion).size() * 4 +
    shogi.value.hands.color('gote').count() +
    (shogi.value.hands.color('gote').get('bishop') + shogi.value.hands.color('gote').get('rook')) *
      4;

  return {
    sente: {
      king: senteKing,
      nbOfPieces: senteNumberOfPieces,
      pieceValue: senteImpasseValue,
      check: shogi.value.turn === 'sente' && shogi.value.isCheck(),
    },
    gote: {
      king: goteKing,
      nbOfPieces: goteNumberOfPieces,
      pieceValue: goteImpasseValue,
      check: shogi.value.turn === 'gote' && shogi.value.isCheck(),
    },
  };
}

export function isImpasse(rules: Rules, sfen: Sfen, initialSfen?: Sfen): boolean {
  const info = impasseInfo(rules, sfen, initialSfen);
  if (info) {
    return ['sente', 'gote'].some((color: Color) => {
      const i = info[color];
      return (
        i.king &&
        i.nbOfPieces >= impasseNecessaryEnteredPieces &&
        i.pieceValue >=
          (color === 'sente' ? impasseNecessarySenteScore : impasseNecessaryGoteScore) &&
        !i.check
      );
    });
  } else return false;
}

function pointOffsetFromSfen(rules: Rules, sfen: string): number {
  const pos = parseSfen(rules, sfen, false);
  if (pos.isErr || !isHandicap({ sfen: sfen })) return 0;

  const board = pos.value.board;
  const combinedHand = pos.value.hands.color('sente').combine(pos.value.hands.color('gote'));

  const allPiecesWithoutKings = board.occupied.diff(board.role('king'));
  const allMajorPieces = board
    .role('bishop')
    .union(board.role('rook'))
    .union(board.role('horse'))
    .union(board.role('dragon'));

  return (
    impasseNecessaryGoteScore * 2 -
    allPiecesWithoutKings.size() -
    allMajorPieces.size() * 4 -
    combinedHand.count() -
    (combinedHand.get('bishop') + combinedHand.get('rook')) * 4
  );
}
