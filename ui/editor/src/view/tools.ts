import { i18n, i18nFormatCapitalized } from 'i18n';
import { i18nVariant } from 'i18n/variant';
import { colorName } from 'shogi/color-name';
import { roleName } from 'shogi/notation';
import { colors } from 'shogiground/constants';
import { RULES } from 'shogiops/constants';
import { findHandicaps, isHandicap } from 'shogiops/handicaps';
import { initialSfen, parseSfen } from 'shogiops/sfen';
import type { Handicap, Role, Rules } from 'shogiops/types';
import { h, type VNode } from 'snabbdom';
import type EditorCtrl from '../ctrl';
import type { EditorState } from '../interfaces';

export function tools(ctrl: EditorCtrl, state: EditorState): VNode {
  return h('div.tools', [
    variants(ctrl),
    colorTurn(ctrl, state),
    positions(ctrl, state),
    !ctrl.data.embed ? pieceCounter(ctrl) : null,
  ]);
}

function variants(ctrl: EditorCtrl): VNode {
  function variant2option(key: Rules, ctrl: EditorCtrl): VNode {
    return h(
      'option',
      {
        attrs: {
          value: key,
          selected: key === ctrl.rules,
        },
      },
      `${i18n('variant')} | ${i18nVariant(key)}`,
    );
  }
  return h('div.variants', [
    h(
      'select',
      {
        attrs: { id: 'variants' },
        on: {
          change(e) {
            ctrl.setRules((e.target as HTMLSelectElement).value as Rules);
          },
        },
      },
      RULES.map(rules => variant2option(rules, ctrl)),
    ),
  ]);
}

function colorTurn(ctrl: EditorCtrl, state: EditorState): VNode {
  const handicap = isHandicap({ rules: ctrl.data.variant, sfen: state.sfen });
  return h(
    'div.color-turn',
    h(
      'select',
      {
        attrs: { id: 'color-turn' },
        on: {
          change(e) {
            ctrl.setTurn((e.target as HTMLSelectElement).value as Color);
          },
        },
      },
      colors.map(color =>
        h(
          'option',
          {
            attrs: {
              value: color,
              selected: color === ctrl.turn,
            },
          },
          i18nFormatCapitalized('xPlays', colorName(color, handicap)),
        ),
      ),
    ),
  );
}

function positions(ctrl: EditorCtrl, state: EditorState): VNode {
  const position2option = (handicap: Omit<Handicap, 'rules'>): VNode =>
    h(
      'option',
      {
        attrs: {
          value: handicap.sfen,
          selected: state.sfen === handicap.sfen,
        },
      },
      `${handicap.japaneseName} (${handicap.englishName})`,
    );
  return h(
    'div.positions',
    h(
      'select#editor-positions',
      {
        props: {
          value: isHandicap({ sfen: state.sfen, rules: ctrl.rules })
            ? state.sfen.split(' ').slice(0, 4).join(' ')
            : '',
        },
        on: {
          change(e) {
            const el = e.target as HTMLSelectElement;
            const value = el.selectedOptions[0].value;
            if (value) ctrl.setSfen(value);
          },
        },
      },
      [
        h(
          'option',
          {
            attrs: {
              value: '',
            },
          },
          `- ${i18n('boardEditor')}  -`,
        ),
        optgroup(i18n('handicaps'), findHandicaps({ rules: ctrl.rules }).map(position2option)),
      ],
    ),
  );
}

function optgroup(name: string, opts: VNode[]): VNode {
  return h('optgroup', { attrs: { label: name } }, opts);
}

function pieceCounter(ctrl: EditorCtrl): VNode {
  const pieceValueOrder: Role[] = [
    'pawn',
    'lance',
    'knight',
    'silver',
    'gold',
    'bishop',
    'rook',
    'tokin',
    'king',
  ];
  function singlePieceCounter(cur: number, total: number, name: string, suffix = ''): VNode {
    return h('span', [
      h('strong', ` ${name}: `),
      `${cur.toString()}/${total.toString()}`,
      h('span', suffix),
    ]);
  }
  const defaultBoard = parseSfen(ctrl.rules, initialSfen(ctrl.rules)).unwrap().board;
  const initialRoles = defaultBoard.presentRoles().sort((a, b) => {
    const indexA = pieceValueOrder.indexOf(a);
    const indexB = pieceValueOrder.indexOf(b);
    return indexA - indexB;
  });

  const pieceCount: [Role, number, string][] = initialRoles.map((r: Role) => [
    r,
    defaultBoard.role(r).size(),
    roleName(ctrl.rules, r).toUpperCase(),
  ]);

  return h(
    'div.piece-counter',
    h(
      'div',
      pieceCount.map((s, i) =>
        singlePieceCounter(
          ctrl.countPieces(s[0]),
          s[1],
          s[2],
          pieceCount.length > i + 1 ? ', ' : '',
        ),
      ),
    ),
  );
}
