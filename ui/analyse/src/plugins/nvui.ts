import { Prop, prop } from 'common/common';
import { MaybeVNodes, bind } from 'common/snabbdom';
import { Player } from 'game';
import { commands } from 'nvui/command';
import { Notify } from 'nvui/notify';
import { renderSetting } from 'nvui/setting';
import { Style, renderBoard, renderMove, renderPieces, styleSetting } from 'nvui/shogi';
import { Shogiground } from 'shogiground';
import { VNode, h } from 'snabbdom';
import AnalyseController from '../ctrl';
import { makeConfig as makeSgConfig } from '../ground';
import { AnalyseData, Redraw } from '../interfaces';

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
        style = moveStyle.get();
      if (!ctrl.shogiground)
        ctrl.shogiground = Shogiground({
          ...makeSgConfig(ctrl),
          animation: { enabled: false },
          drawable: { enabled: false },
          coordinates: {
            enabled: false,
          },
        });
      return h('main.analyse', [
        h('div.nvui', [
          h('h1', 'Textual representation'),
          h('h2', 'Game info'),
          ...['sente', 'gote'].map((color: Color) =>
            h('p', [color + ' player: ', renderPlayer(ctrl, playerByColor(d, color))])
          ),
          h('p', `${d.game.rated ? 'Rated' : 'Casual'} ${d.game.perf}`),
          d.clock ? h('p', `Clock: ${d.clock.initial / 60} + ${d.clock.increment}`) : null,
          h('h2', 'Moves'),
          h(
            'p.moves',
            {
              attrs: {
                role: 'log',
                'aria-live': 'off',
              },
            },
            renderMainline(ctrl.mainline, ctrl.path, style)
          ),
          h('h2', 'Pieces'),
          h('div.pieces', renderPieces(ctrl.shogiground.state.pieces, style)),
          h('h2', 'Current position'),
          h(
            'p.position',
            {
              attrs: {
                'aria-live': 'assertive',
                'aria-atomic': true,
              },
            },
            renderCurrentNode(ctrl.node, style)
          ),
          h('h2', 'Move form'),
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
                'Command input',
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
          ...(renderAcpl(ctrl, style) || [requestAnalysisButton(ctrl, analysisInProgress, notify.set)]),
          h('h2', 'Board'),
          h('pre.board', renderBoard(ctrl.shogiground.state.pieces, ctrl.data.player.color)),
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
    else notify('Invalid command');
    $input.val('');
    return false;
  };
}

const shortCommands = ['p', 'scan'];

function isShortCommand(input: string): boolean {
  return shortCommands.includes(input.split(' ')[0]);
}

function onCommand(ctrl: AnalyseController, notify: (txt: string) => void, c: string, style: Style) {
  const pieces = ctrl.shogiground.state.pieces;
  notify(commands.piece.apply(c, pieces, style) || commands.scan.apply(c, pieces, style) || `Invalid command: ${c}`);
}

const analysisGlyphs = ['?!', '?', '??'];

function renderAcpl(ctrl: AnalyseController, style: Style): MaybeVNodes | undefined {
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
              [node.ply + ctrl.plyOffset(), renderMove(node.usi, style), renderComments(node)].join(' ')
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

function renderMainline(nodes: Tree.Node[], currentPath: Tree.Path, style: Style) {
  const res: Array<string | VNode> = [];
  let path: Tree.Path = '';
  nodes.forEach(node => {
    if (!node.usi) return;
    path += node.id;
    const content: MaybeVNodes = [node.ply.toString(), renderMove(node.usi, style)];
    res.push(
      h(
        'move',
        {
          attrs: { p: path },
          class: { active: path === currentPath },
        },
        content
      )
    );
    res.push(renderComments(node));
    res.push(', ');
    if (node.ply % 2 === 0) res.push(h('br'));
  });
  return res;
}

function renderCurrentNode(node: Tree.Node, style: Style): string {
  if (!node.usi) return 'Initial position';
  return [node.ply, renderMove(node.usi, style), renderComments(node)].join(' ');
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
