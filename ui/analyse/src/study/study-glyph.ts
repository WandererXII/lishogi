import { bind } from 'common/snabbdom';
import throttle from 'common/throttle';
import { allGlyphs } from 'shogi/glyphs';
import { h, type VNode } from 'snabbdom';
import type AnalyseCtrl from '../ctrl';

export interface GlyphCtrl {
  root: AnalyseCtrl;
  toggleGlyph(id: Tree.GlyphId): void;
  redraw(): void;
}

function glyphClass(id: number) {
  switch (id) {
    case 6:
      return 'inaccuracy';
    case 5:
      return 'interesting';
    case 4:
      return 'blunder';
    case 3:
      return 'brilliant';
    case 2:
      return 'mistake';
    case 1:
      return 'good-move';
    default:
      return 'other';
  }
}

function renderGlyph(ctrl: GlyphCtrl, node: Tree.Node) {
  return (glyph: Tree.Glyph) =>
    h(
      `button.g-${glyphClass(glyph.id)}`,
      {
        hook: bind('click', _ => ctrl.toggleGlyph(glyph.id), ctrl.redraw),
        attrs: { 'data-symbol': glyph.symbol, type: 'button' },
        class: {
          active: !!node.glyphs && !!node.glyphs.find(g => g.id === glyph.id),
        },
      },
      [glyph.name],
    );
}

export function ctrl(root: AnalyseCtrl): GlyphCtrl {
  const toggleGlyph = throttle(500, (id: string) => {
    root.study!.makeChange(
      'toggleGlyph',
      root.study!.withPosition({
        id,
      }),
    );
  });

  return {
    root,
    toggleGlyph,
    redraw: root.redraw,
  };
}

export function viewDisabled(why: string): VNode {
  return h('div.study__glyphs', [h('div.study__message', why)]);
}

export function view(ctrl: GlyphCtrl): VNode {
  const node = ctrl.root.node;

  return h('div.study__glyphs', [
    h('div.move', allGlyphs.move.map(renderGlyph(ctrl, node))),
    h('div.position', allGlyphs.position.map(renderGlyph(ctrl, node))),
    h('div.observation', allGlyphs.observation.map(renderGlyph(ctrl, node))),
  ]);
}
