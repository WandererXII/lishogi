import { type Prop, prop } from 'common/common';
import { type MaybeVNodes, bind } from 'common/snabbdom';
import type { Player } from 'game/interfaces';
import { i18n } from 'i18n';
import { commands } from 'nvui/command';
import { Notify } from 'nvui/notify';
import { renderSetting } from 'nvui/setting';
import {
  type Style,
  renderBoard,
  renderHand,
  renderMove,
  renderPieces,
  styleSetting,
  supportedVariant,
  validUsi,
} from 'nvui/shogi';
import { engineNameFromCode } from 'shogi/engine-name';
import { Shogiground } from 'shogiground';
import { opposite } from 'shogiground/util';
import { type VNode, h } from 'snabbdom';
import type AnalyseController from '../ctrl';
import { makeConfig as makeSgConfig } from '../ground';
import type { AnalyseData } from '../interfaces';

function main(redraw: Redraw) {
  const notify = new Notify(redraw);
  const moveStyle = styleSetting();
  const analysisInProgress = prop(false);

  window.lishogi.pubsub.on('analysis.server.progress', (data: AnalyseData) => {
    if (data.analysis && !data.analysis.partial) notify.set('Server-side analysis complete');
  });

  return {
    render(ctrl: AnalyseController): VNode {
      const d = ctrl.data;
      const style = moveStyle.get();
      const variantNope =
        !supportedVariant(d.game.variant.key) &&
        'Sorry, this variant is not supported in blind mode.';
      if (!ctrl.shogiground)
        ctrl.shogiground = Shogiground({
          ...makeSgConfig(ctrl),
          animation: { enabled: false },
          drawable: { enabled: false },
          coordinates: {
            enabled: false,
          },
        });
      if (variantNope) setTimeout(() => notify.set(variantNope), 3000);

      return h('main.analyse', [
        h('div.nvui', [
          h('h1', i18n('nvui:textualRepresentation')),
          h('h2', i18n('nvui:gameInfo')),
          ...['sente', 'gote'].map((color: Color) =>
            h('p', [`${color} player: `, renderPlayer(ctrl, playerByColor(d, color))]),
          ),
          h('p', `${d.game.rated ? i18n('rated') : i18n('casual')}`),
          d.clock
            ? h(
                'p',
                `${i18n('clock')}: ${d.clock.initial / 60} + ${d.clock.increment} | ${d.clock.byoyomi})`,
              )
            : null,
          h('h2', i18n('nvui:moves')),
          h(
            'p.moves',
            {
              attrs: {
                role: 'log',
                'aria-live': 'off',
              },
            },
            renderMainline(ctrl.mainline, ctrl.path, ctrl.data.game.variant.key, style),
          ),
          h('h2', i18n('nvui:pieces')),
          h('div.pieces', renderPieces(ctrl.shogiground.state.pieces, style)),
          h('h2', i18n('nvui:currentPosition')),
          h(
            'p.position',
            {
              attrs: {
                'aria-live': 'assertive',
                'aria-atomic': 'true',
              },
            },
            renderCurrentNode(ctrl.node, ctrl.data.game.variant.key, style),
          ),
          h('h2', i18n('nvui:moveForm')),
          h(
            'form',
            {
              hook: {
                insert(vnode) {
                  const $form = $(vnode.elm as HTMLFormElement);
                  const $input = $form.find<HTMLInputElement>('.move').val('').trigger('focus');
                  $form.on('submit', onSubmit(ctrl, notify.set, moveStyle.get, $input));
                },
              },
            },
            [
              h('label', [
                i18n('nvui:commandInput'),
                h('input.move.mousetrap', {
                  attrs: {
                    name: 'move',
                    type: 'text',
                    autocomplete: 'off',
                    autofocus: true,
                  },
                }),
              ]),
            ],
          ),
          notify.render(),
          // h('h2', 'Actions'),
          // h('div.actions', tableInner(ctrl)),
          h('h2', i18n('computerAnalysis')),
          ...(renderAcpl(ctrl, ctrl.data.game.variant.key, style) || [
            requestAnalysisButton(ctrl, analysisInProgress, notify.set),
          ]),
          h('h2', [i18n('nvui:board'), ' & ', i18n('nvui:hands')]),
          h(
            'pre.hand',
            renderHand(
              'top',
              opposite(ctrl.data.player.color),
              ctrl.shogiground.state.hands.handMap.get(opposite(ctrl.data.player.color)),
              ctrl.data.game.variant.key,
              style,
            ),
          ),
          h(
            'pre.board',
            renderBoard(
              ctrl.shogiground.state.pieces,
              ctrl.data.player.color,
              ctrl.data.game.variant.key,
              style,
            ),
          ),
          h(
            'pre.hand',
            renderHand(
              'bottom',
              ctrl.data.player.color,
              ctrl.shogiground.state.hands.handMap.get(ctrl.data.player.color),
              ctrl.data.game.variant.key,
              style,
            ),
          ),
          h('div.content', {
            hook: {
              insert: vnode => {
                $(vnode.elm as HTMLElement).append($('.blind-content').removeClass('none'));
              },
            },
          }),
          h('h2', i18n('settings:settings')),
          h('label', [i18n('notationSystem'), renderSetting(moveStyle, ctrl.redraw)]),
          h('h2', i18n('keyboardShortcuts')),
          h('p', i18n('nvui:useArrowKeys')),
          h('h2', i18n('nvui:commands')),
          h('p', [
            'Type these commands in the command input.',
            h('br'),
            commands.piece.help,
            h('br'),
            commands.scan.help,
            h('br'),
          ]),
        ]),
      ]);
    },
  };
}

function onSubmit(
  ctrl: AnalyseController,
  notify: (txt: string) => void,
  style: () => Style,
  $input: JQuery<HTMLInputElement>,
) {
  return () => {
    let input = $input.val()?.trim()!;
    if (isShortCommand(input)) input = `/${input}`;
    if (input[0] === '/') onCommand(ctrl, notify, input.slice(1), style());
    else {
      const usi = validUsi(input, ctrl.node.sfen, ctrl.data.game.variant.key);
      if (usi) ctrl.sendUsi(usi);
      else notify('Invalid command');
    }
    $input.val('');
    return false;
  };
}

const shortCommands = ['p', 's'];

function isShortCommand(input: string): boolean {
  return shortCommands.includes(input.split(' ')[0]);
}

function onCommand(
  ctrl: AnalyseController,
  notify: (txt: string) => void,
  c: string,
  style: Style,
) {
  const pieces = ctrl.shogiground.state.pieces;
  const hands = ctrl.shogiground.state.hands.handMap;
  notify(
    commands.piece.apply(c, pieces, hands, style) ||
      commands.scan.apply(c, pieces, style) ||
      `Invalid command: ${c}`,
  );
}

const analysisGlyphs = ['?!', '?', '??'];

function renderAcpl(
  ctrl: AnalyseController,
  variant: VariantKey,
  style: Style,
): MaybeVNodes | undefined {
  const anal = ctrl.data.analysis;
  if (!anal) return undefined;
  const analysisNodes = ctrl.mainline.filter(n =>
    (n.glyphs || []).find(g => analysisGlyphs.includes(g.symbol)),
  );
  const res: Array<VNode> = [];
  ['sente', 'gote'].forEach((color: Color) => {
    const acpl = anal[color].acpl;
    res.push(h('h3', `${color} player: ${acpl} ACPL`));
    res.push(
      h(
        'select',
        {
          hook: bind(
            'change',
            e => ctrl.jumpToMain(Number.parseInt((e.target as HTMLSelectElement).value)),
            ctrl.redraw,
          ),
        },
        analysisNodes
          .filter(n => (n.ply % 2 === 1) === (color === 'sente'))
          .map(node =>
            h(
              'option',
              {
                attrs: {
                  value: node.ply,
                  selected: node.ply === ctrl.node.ply,
                },
              },
              [
                node.ply + ctrl.plyOffset(),
                renderMove(node.usi, node.sfen, variant, style),
                renderComments(node),
              ].join(' '),
            ),
          ),
      ),
    );
  });
  return res;
}

function requestAnalysisButton(
  ctrl: AnalyseController,
  inProgress: Prop<boolean>,
  notify: (msg: string) => void,
) {
  if (inProgress()) return h('p', 'Server-side analysis in progress');
  if (ctrl.ongoing || ctrl.synthetic) return undefined;
  return h(
    'button',
    {
      hook: bind('click', _ => {
        window.lishogi.xhr
          .json('POST', `/${ctrl.data.game.id}/request-analysis`)
          .then(() => {
            inProgress(true);
            notify('Server-side analysis in progress');
          })
          .catch(() => {
            notify('Cannot run server-side analysis');
          });
      }),
    },
    'Request a computer analysis',
  );
}

function renderMainline(
  nodes: Tree.Node[],
  currentPath: Tree.Path,
  variant: VariantKey,
  style: Style,
) {
  const res: Array<string | VNode> = [];
  let path: Tree.Path = '';
  nodes.forEach(node => {
    if (!node.usi) return;
    path += node.id;
    const content: MaybeVNodes = [
      node.ply.toString(),
      renderMove(node.usi, node.sfen, variant, style),
    ];
    res.push(
      h(
        'move',
        {
          attrs: { p: path },
          class: { active: path === currentPath },
        },
        content.join(' '),
      ),
    );
    res.push(renderComments(node));
    res.push(', ');
    if (node.ply % 2 === 0) res.push(h('br'));
  });
  return res;
}

function renderCurrentNode(node: Tree.Node, variant: VariantKey, style: Style): string {
  return [node.ply, renderMove(node.usi, node.sfen, variant, style), renderComments(node)].join(
    ' ',
  );
}

function renderComments(node: Tree.Node): string {
  if (!node.comments) return '';
  return (node.comments || []).map(c => renderComment(c)).join('. ');
}

function renderComment(comment: Tree.Comment): string {
  return comment.text;
}

function renderPlayer(ctrl: AnalyseController, player: Player) {
  return player.ai ? engineNameFromCode(player.aiCode, player.ai) : userHtml(ctrl, player);
}

function userHtml(ctrl: AnalyseController, player: Player) {
  const d = ctrl.data;
  const user = player.user;
  const perf = user ? user.perfs[d.game.perf] : null;
  const rating = player.rating ? player.rating : perf?.rating;
  const rd = player.ratingDiff;
  const ratingDiff = rd ? (rd > 0 ? `+${rd}` : rd < 0 ? `−${-rd}` : '') : '';
  return user
    ? h('span', [
        h(
          'a',
          {
            attrs: { href: `/@/${user.username}` },
          },
          user.title ? `${user.title} ${user.username}` : user.username,
        ),
        rating ? ` ${rating}` : '',
        ` ${ratingDiff}`,
      ])
    : 'Anonymous';
}

function playerByColor(d: AnalyseData, color: Color) {
  return color === d.player.color ? d.player : d.opponent;
}

window.lishogi.registerModule(__bundlename__, main);
