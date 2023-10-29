import { Prop, prop } from 'common/common';
import { MaybeVNodes, bind } from 'common/snabbdom';
import { Player } from 'game';
import { commands } from 'nvui/command';
import { Notify } from 'nvui/notify';
import { renderSetting } from 'nvui/setting';
import {
  Style,
  renderBoard,
  renderMove,
  renderPieces,
  styleSetting,
  supportedVariant,
  validUsi,
  renderHand,
  NVUITrans,
} from 'nvui/shogi';
import { Shogiground } from 'shogiground';
import { VNode, h } from 'snabbdom';
import AnalyseController from '../ctrl';
import { makeConfig as makeSgConfig } from '../ground';
import { AnalyseData, Redraw } from '../interfaces';
import { opposite } from 'shogiground/util';

window.lishogi.AnalyseNVUI = function (redraw: Redraw) {
  const notify = new Notify(redraw),
    moveStyle = styleSetting(),
    analysisInProgress = prop(false);

  window.lishogi.pubsub.on('analysis.server.progress', (data: AnalyseData) => {
    if (data.analysis && !data.analysis.partial) notify.set('Server-side analysis complete');
  });

  return {
    render(ctrl: AnalyseController): VNode {
      const d = ctrl.data,
        style = moveStyle.get(),
        variantNope = !supportedVariant(d.game.variant.key) && 'Sorry, this variant is not supported in blind mode.';
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
          h('h1', 'Textual representation'),
          h('h2', 'Game info'),
          ...['sente', 'gote'].map((color: Color) =>
            h('p', [color + ' player: ', renderPlayer(ctrl, playerByColor(d, color))])
          ),
          h('p', `${d.game.rated ? 'Rated' : 'Casual'} ${d.game.perf}`),
          d.clock ? h('p', `Clock: ${d.clock.initial / 60} + ${d.clock.increment}`) : null,
          h('h2', NVUITrans('Moves')),
          h(
            'p.moves',
            {
              attrs: {
                role: 'log',
                'aria-live': 'off',
              },
            },
            renderMainline(ctrl.mainline, ctrl.path, ctrl.data.game.variant.key, style)
          ),
          h('h2', NVUITrans('Pieces')),
          h('div.pieces', renderPieces(ctrl.shogiground.state.pieces, style)),
          h('h2', NVUITrans('Current position')),
          h(
            'p.position',
            {
              attrs: {
                'aria-live': 'assertive',
                'aria-atomic': 'true',
              },
            },
            renderCurrentNode(ctrl.node, ctrl.data.game.variant.key, style)
          ),
          h('h2', NVUITrans('Move form')),
          h(
            'form',
            {
              hook: {
                insert(vnode) {
                  const $form = $(vnode.elm as HTMLFormElement),
                    $input = $form.find('.move').val('').focus();
                  $form.submit(onSubmit(ctrl, notify.set, moveStyle.get, $input));
                },
              },
            },
            [
              h('label', [
                NVUITrans('Command input'),
                h('input.move.mousetrap', {
                  attrs: {
                    name: 'move',
                    type: 'text',
                    autocomplete: 'off',
                    autofocus: true,
                  },
                }),
              ]),
            ]
          ),
          notify.render(),
          // h('h2', 'Actions'),
          // h('div.actions', tableInner(ctrl)),
          h('h2', 'Computer analysis'),
          ...(renderAcpl(ctrl, ctrl.data.game.variant.key, style) || [
            requestAnalysisButton(ctrl, analysisInProgress, notify.set),
          ]),
          h('h2', [NVUITrans('Board'), ' & ', NVUITrans('hands')]),
          h(
            'pre.hand',
            renderHand(
              'top',
              opposite(ctrl.data.player.color),
              ctrl.shogiground.state.hands.handMap.get(opposite(ctrl.data.player.color)),
              ctrl.data.game.variant.key,
              style
            )
          ),
          h(
            'pre.board',
            renderBoard(ctrl.shogiground.state.pieces, ctrl.data.player.color, ctrl.data.game.variant.key, style)
          ),
          h(
            'pre.hand',
            renderHand(
              'bottom',
              ctrl.data.player.color,
              ctrl.shogiground.state.hands.handMap.get(ctrl.data.player.color),
              ctrl.data.game.variant.key,
              style
            )
          ),
          h('div.content', {
            hook: {
              insert: vnode => {
                $(vnode.elm as HTMLElement).append($('.blind-content').removeClass('none'));
              },
            },
          }),
          h('h2', 'Settings'),
          h('label', ['Move notation', renderSetting(moveStyle, ctrl.redraw)]),
          h('h2', 'Keyboard shortcuts'),
          h('p', ['Use arrow keys to navigate in the game.']),
          h('h2', 'Commands'),
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
};

function onSubmit(ctrl: AnalyseController, notify: (txt: string) => void, style: () => Style, $input: JQuery) {
  return function () {
    let input = $input.val().trim();
    if (isShortCommand(input)) input = '/' + input;
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

function onCommand(ctrl: AnalyseController, notify: (txt: string) => void, c: string, style: Style) {
  const pieces = ctrl.shogiground.state.pieces,
    hands = ctrl.shogiground.state.hands.handMap;
  notify(
    commands.piece.apply(c, pieces, hands, style) || commands.scan.apply(c, pieces, style) || `Invalid command: ${c}`
  );
}

const analysisGlyphs = ['?!', '?', '??'];

function renderAcpl(ctrl: AnalyseController, variant: VariantKey, style: Style): MaybeVNodes | undefined {
  const anal = ctrl.data.analysis;
  if (!anal) return undefined;
  const analysisNodes = ctrl.mainline.filter(n => (n.glyphs || []).find(g => analysisGlyphs.includes(g.symbol)));
  const res: Array<VNode> = [];
  ['sente', 'gote'].forEach((color: Color) => {
    const acpl = anal[color].acpl;
    res.push(h('h3', `${color} player: ${acpl} ACPL`));
    res.push(
      h(
        'select',
        {
          hook: bind('change', e => ctrl.jumpToMain(parseInt((e.target as HTMLSelectElement).value)), ctrl.redraw),
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
              [node.ply + ctrl.plyOffset(), renderMove(node.usi, node.sfen, variant, style), renderComments(node)].join(
                ' '
              )
            )
          )
      )
    );
  });
  return res;
}

function requestAnalysisButton(ctrl: AnalyseController, inProgress: Prop<boolean>, notify: (msg: string) => void) {
  if (inProgress()) return h('p', 'Server-side analysis in progress');
  if (ctrl.ongoing || ctrl.synthetic) return undefined;
  return h(
    'button',
    {
      hook: bind('click', _ => {
        $.ajax({
          method: 'post',
          url: `/${ctrl.data.game.id}/request-analysis`,
          success: () => {
            inProgress(true);
            notify('Server-side analysis in progress');
          },
          error: () => notify('Cannot run server-side analysis'),
        });
      }),
    },
    'Request a computer analysis'
  );
}

function renderMainline(nodes: Tree.Node[], currentPath: Tree.Path, variant: VariantKey, style: Style) {
  const res: Array<string | VNode> = [];
  let path: Tree.Path = '';
  nodes.forEach(node => {
    if (!node.usi) return;
    path += node.id;
    const content: MaybeVNodes = [node.ply.toString(), renderMove(node.usi, node.sfen, variant, style)];
    res.push(
      h(
        'move',
        {
          attrs: { p: path },
          class: { active: path === currentPath },
        },
        content.join(' ')
      )
    );
    res.push(renderComments(node));
    res.push(', ');
    if (node.ply % 2 === 0) res.push(h('br'));
  });
  return res;
}

function renderCurrentNode(node: Tree.Node, variant: VariantKey, style: Style): string {
  return [node.ply, renderMove(node.usi, node.sfen, variant, style), renderComments(node)].join(' ');
}

function renderComments(node: Tree.Node): string {
  if (!node.comments) return '';
  return (node.comments || []).map(c => renderComment(c)).join('. ');
}

function renderComment(comment: Tree.Comment): string {
  return comment.text;
}

function renderPlayer(ctrl: AnalyseController, player: Player) {
  return player.ai ? ctrl.trans('aiNameLevelAiLevel', 'Engine', player.ai) : userHtml(ctrl, player);
}

function userHtml(ctrl: AnalyseController, player: Player) {
  const d = ctrl.data,
    user = player.user,
    perf = user ? user.perfs[d.game.perf] : null,
    rating = player.rating ? player.rating : perf && perf.rating,
    rd = player.ratingDiff,
    ratingDiff = rd ? (rd > 0 ? '+' + rd : rd < 0 ? '−' + -rd : '') : '';
  return user
    ? h('span', [
        h(
          'a',
          {
            attrs: { href: '/@/' + user.username },
          },
          user.title ? `${user.title} ${user.username}` : user.username
        ),
        rating ? ` ${rating}` : ``,
        ' ' + ratingDiff,
      ])
    : 'Anonymous';
}

function playerByColor(d: AnalyseData, color: Color) {
  return color === d.player.color ? d.player : d.opponent;
}
