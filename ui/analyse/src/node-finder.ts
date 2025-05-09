import * as winningChances from 'ceval/winning-chances';
import { defined } from 'common/common';

function hasCompChild(node: Tree.Node): boolean {
  return !!node.children.find(c => !!c.comp);
}

export function nextGlyphSymbol(
  color: Color,
  symbol: string,
  mainline: Tree.Node[],
  fromPly: number,
): Tree.Node | undefined {
  const len = mainline.length;
  if (!len) return;
  const fromIndex = fromPly - mainline[0].ply;
  for (let i = 1; i < len; i++) {
    const node = mainline[(fromIndex + i) % len];
    const found =
      node.ply % 2 === (color === 'sente' ? 1 : 0) &&
      node.glyphs &&
      node.glyphs.find(g => g.symbol === symbol);
    if (found) return node;
  }
  return;
}

export function evalSwings(
  mainline: Tree.Node[],
  nodeFilter: (node: Tree.Node) => boolean,
): Tree.Node[] {
  const found: Tree.Node[] = [];
  const threshold = 0.1;
  for (let i = 1; i < mainline.length; i++) {
    const node = mainline[i];
    const prev = mainline[i - 1];
    if (nodeFilter(node) && node.eval && prev.eval) {
      const diff = Math.abs(winningChances.povDiff('sente', prev.eval, node.eval));
      if (diff > threshold && hasCompChild(prev)) found.push(node);
    }
  }
  return found;
}

function fourfoldSfen(sfen: Sfen) {
  return sfen.split(' ').slice(0, 3).join(' ');
}

export function detectFourfold(nodeList: Tree.Node[], node: Tree.Node): void {
  if (defined(node.fourfold)) return;
  const currentSfen = fourfoldSfen(node.sfen);
  let nbSimilarPositions = 0;
  for (const i in nodeList)
    if (fourfoldSfen(nodeList[i].sfen) === currentSfen) nbSimilarPositions++;
  node.fourfold = nbSimilarPositions > 3;
}
