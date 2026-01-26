import { memoize } from 'common/common';
import { type PrefTypes, prefs } from 'common/prefs';
import type { Notation as sgNotation } from 'shogiground/types';
import { makeJapaneseMoveOrDrop } from 'shogiops/notation/japanese';
import { makeKifMoveOrDrop } from 'shogiops/notation/kif';
import { makeKitaoKawasakiMoveOrDrop } from 'shogiops/notation/kitao-kawasaki';
import { roleToKanji, roleToWestern } from 'shogiops/notation/util';
import { makeWesternMoveOrDrop } from 'shogiops/notation/western';
import { makeWesternEngineMoveOrDrop } from 'shogiops/notation/western-engine';
import { makeYorozuyaMoveOrDrop } from 'shogiops/notation/yorozuya';
import { parseSfen, roleToForsyth } from 'shogiops/sfen';
import type { MoveOrDrop, Role, Rules, Square } from 'shogiops/types';
import { makeUsi, parseUsi } from 'shogiops/util';
import type { Position } from 'shogiops/variant/position';
import { plyColor } from './common';

type Notation = PrefTypes['notation'];

const notationPref = memoize(
  () => Number.parseInt(document.body.dataset.notation || '0') as Notation,
);

// Notations, that should be displayed with ☖/☗
export function notationsWithColor(): boolean {
  const colorNotations: Notation[] = [
    prefs.notation.KAWASAKI,
    prefs.notation.JAPANESE,
    prefs.notation.KIF,
  ];
  return colorNotations.includes(notationPref());
}

export function notationFiles(): sgNotation {
  if (notationPref() === prefs.notation.WESTERN) return 'hex';
  else if (notationPref() === prefs.notation.YOROZUYA) return 'dizhi';
  else return 'numeric';
}

export function notationRanks(): sgNotation {
  switch (notationPref()) {
    case prefs.notation.JAPANESE:
    case prefs.notation.KIF:
    case prefs.notation.YOROZUYA:
      return 'japanese';
    case prefs.notation.WESTERNENGINE:
    case prefs.notation.USI:
      return 'engine';
    default:
      return 'hex';
  }
}

export function roleName(rules: Rules, role: Role): string {
  switch (notationPref()) {
    case prefs.notation.KAWASAKI:
      return roleToKanji(rules)(role).replace('成', '+');
    case prefs.notation.JAPANESE:
    case prefs.notation.KIF:
      return roleToKanji(rules)(role);
    case prefs.notation.USI:
      return roleToForsyth(rules)(role)!;
    default:
      return roleToWestern(rules)(role);
  }
}

export function makeNotationWithPosition(
  pos: Position,
  md: MoveOrDrop,
  lastMoveOrDrop?: MoveOrDrop | { to: Square },
): string {
  switch (notationPref()) {
    case prefs.notation.KAWASAKI:
      return makeKitaoKawasakiMoveOrDrop(pos, md, lastMoveOrDrop?.to)!;
    case prefs.notation.JAPANESE:
      return makeJapaneseMoveOrDrop(pos, md, lastMoveOrDrop?.to)!;
    case prefs.notation.WESTERNENGINE:
      return makeWesternEngineMoveOrDrop(pos, md)!;
    case prefs.notation.KIF:
      return makeKifMoveOrDrop(pos, md, lastMoveOrDrop?.to)!;
    case prefs.notation.USI:
      return makeUsi(md);
    case prefs.notation.YOROZUYA:
      return makeYorozuyaMoveOrDrop(pos, md, lastMoveOrDrop?.to)!;
    default:
      return makeWesternMoveOrDrop(pos, md)!;
  }
}

export function makeNotationLineWithPosition(
  pos: Position,
  mds: MoveOrDrop[],
  lastMoveOrDrop?: MoveOrDrop | { to: Square },
): MoveNotation[] {
  pos = pos.clone();
  const line: MoveNotation[] = [];
  for (const md of mds) {
    line.push(makeNotationWithPosition(pos, md, lastMoveOrDrop));
    lastMoveOrDrop = md;
    pos.play(md);
  }
  return line;
}

export function makeNotation(
  sfen: Sfen,
  variant: VariantKey,
  usi: Usi,
  lastUsi?: Usi,
): MoveNotation {
  const pos = createPosition(sfen, variant);
  const lastMoveOrDrop = lastUsi ? parseUsi(lastUsi) : undefined;
  return makeNotationWithPosition(pos, parseUsi(usi)!, lastMoveOrDrop);
}

export function makeNotationLine(
  sfen: Sfen,
  variant: VariantKey,
  usis: Usi[],
  lastUsi?: Usi,
): MoveNotation[] {
  return makeNotationLineWithPosition(
    createPosition(sfen, variant),
    usis.map(u => parseUsi(u)!),
    lastUsi ? parseUsi(lastUsi) : undefined,
  );
}

function createPosition(sfen: Sfen, variant: VariantKey): Position {
  return parseSfen(variant, sfen, false).unwrap();
}

// create move notation in reference to node or parent node
export function usiToNotation(
  node: Tree.Node,
  parentNode: Tree.Node | undefined,
  variant: VariantKey,
  text: string,
): string {
  const matches = text.match(/\[usi:(\d*)\.?((?:\d\w|\w\*)\d\w(?:\+|=)?)\]/g);
  if (!matches?.length) return text;

  for (const mText of matches) {
    const match = mText.match(/usi:(\d*)\.?((?:\d\w|\w\*)\d\w(?:\+|=)?)/);
    if (match) {
      const textUsi = match[2];
      const moveOrDrop = textUsi ? parseUsi(textUsi) : undefined;

      if (!moveOrDrop) {
        console.error('Cannot parse usi:', textUsi);
        continue;
      }

      const usi = makeUsi(moveOrDrop);
      const textPlyColor = plyColor(Number.parseInt(match[1]) || node.ply);
      const tryParentNodeFirst = plyColor(node.ply) !== textPlyColor;

      const makeDirectNotation = () => {
        return makeNotation(node.sfen, variant, usi, node.usi);
      };
      const makeParentNotation = () => {
        const parentSfen =
          parentNode?.sfen || node.sfen.replace(/ (b|w) /, (_, c) => ` ${c === 'b' ? 'w' : 'b'} `);
        return makeNotation(parentSfen, variant, usi, parentNode?.usi);
      };

      const notation = tryParentNodeFirst
        ? makeParentNotation() || makeDirectNotation()
        : makeDirectNotation() || makeParentNotation();

      if (notation) text = text.replace(mText, notation);
      else text = text.replace(mText, `[${usi}]`);
    }
  }

  return text;
}
