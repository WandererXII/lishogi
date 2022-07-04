import { h, VNode, VNodeStyle } from 'snabbdom';
import { bind, MaybeVNode } from 'common/snabbdom';
import chessground from './chessground';
import CoordinateTrainerCtrl, { DURATION } from './ctrl';
import { ColorChoice, TimeControl, Mode, CoordModifier } from './interfaces';

const scoreCharts = (ctrl: CoordinateTrainerCtrl): VNode => {
  const average = (array: number[]) => array.reduce((a, b) => a + b) / array.length;
  return h(
    'div.scores',
    [
      ['sente', 'averageScoreAsWhiteX', ctrl.modeScores[ctrl.mode].sente],
      ['gote', 'averageScoreAsBlackX', ctrl.modeScores[ctrl.mode].gote],
    ].map(([color, transKey, scoreList]: [Color, string, number[]]) =>
      scoreList.length
        ? h('div.color-chart', [
            h('p', ctrl.trans.vdom(transKey, h('strong', `${average(scoreList).toFixed(2)}`))),
            h('svg.sparkline', {
              attrs: {
                height: '80px',
                'stroke-width': '3',
                id: `${color}-sparkline`,
              },
              hook: {
                insert: vnode => ctrl.updateChart(vnode.elm as SVGSVGElement, color),
              },
            }),
          ])
        : null
    )
  );
};

const colors: [ColorChoice, string][] = [
  ['black', 'asBlack'],
  ['random', 'randomColor'],
  ['white', 'asWhite'],
];

const timeControls: [TimeControl, string][] = [
  ['untimed', '∞'],
  ['thirtySeconds', '0:30'],
];

const side = (ctrl: CoordinateTrainerCtrl): VNode => {
  const { trans } = ctrl;

  const sideContent: MaybeVNode[] = [h('div.box', h('h1', trans('coordinates')))];
  if (ctrl.playing || ctrl.hasPlayed) {
    sideContent.push(
      h('div.box.current-status', [h('h1', trans('score')), h('div.score', ctrl.score)]),
      ctrl.playing
<<<<<<< HEAD
        ? h('div.box.current-status', [
=======
        ? h('div.box.current-status', { class: { disabled: ctrl.timeDisabled() } }, [
>>>>>>> 42d37d2656 (remove one minute time option)
            h('h1', trans('time')),
            h('div.timer', { class: { hurry: ctrl.timeLeft <= 10 * 1000 } }, (ctrl.timeLeft / 1000).toFixed(1)),
          ])
        : null
    );
  }
  if (!ctrl.playing) {
    sideContent.push(
      h('form.mode.buttons', [
        h(
          'group.radio',
          ['findSquare', 'nameSquare'].map((mode: Mode) =>
            h('div.mode_option', [
              h('input', {
                attrs: {
                  type: 'radio',
                  id: `coord_mode_${mode}`,
                  name: 'mode',
                  value: mode,
                  checked: mode === ctrl.mode,
                },
                on: {
                  change: e => {
                    const target = e.target as HTMLInputElement;
                    ctrl.setMode(target.value as Mode);
                  },
                  keyup: ctrl.onRadioInputKeyUp,
                },
              }),
              h(`label.mode_${mode}`, { attrs: { for: `coord_mode_${mode}` } }, trans(mode)),
            ])
          )
        ),
      ]),
<<<<<<< HEAD
=======
      h('form.timeControl.buttons', [
        h(
          'group.radio',
          timeControls.map(([timeControl, timeControlLabel]) =>
            h('div.timeControl_option', [
              h('input', {
                attrs: {
                  type: 'radio',
                  id: `coord_timeControl_${timeControl}`,
                  name: 'timeControl',
                  value: timeControl,
                  checked: timeControl === ctrl.timeControl,
                },
                on: {
                  change: e => {
                    const target = e.target as HTMLInputElement;
                    ctrl.setTimeControl(target.value as TimeControl);
                  },
                  keyup: ctrl.onRadioInputKeyUp,
                },
              }),
              h(
                `label.timeControl_${timeControl}`,
                {
                  attrs: {
                    for: `coord_timeControl_${timeControl}`,
                  },
                },
                timeControlLabel
              ),
            ])
          )
        ),
      ]),
>>>>>>> 42d37d2656 (remove one minute time option)
      h('form.color.buttons', [
        h(
          'group.radio',
          ['gote', 'random', 'sente'].map((color: ColorChoice) =>
            h('div', [
              h('input', {
                attrs: {
                  type: 'radio',
                  id: `coord_color_${color}`,
                  name: 'color',
                  value: color,
                  checked: color === ctrl.colorChoice,
                },
                on: {
                  change: e => {
                    const target = e.target as HTMLInputElement;
                    ctrl.setColorChoice(target.value as ColorChoice);
                  },
                  keyup: ctrl.onRadioInputKeyUp,
                },
              }),
              h(
                `label.color_${color}`,
                {
                  attrs: {
                    type: 'radio',
                    id: `coord_color_${color}`,
                    name: 'color',
                    value: color,
                    checked: color === ctrl.colorChoice,
                  },
                  on: {
                    change: e => {
                      const target = e.target as HTMLInputElement;
                      ctrl.setColorChoice(target.value as ColorChoice);
                    },
                  },
                }),
                h(
                  `label.color_${color}`,
                  {
                    attrs: {
                      for: `coord_color_${color}`,
                    },
                  },
                  h('i')
                ),
              ])
            )
          ),
        ]),
      ]
    );
  }
  if (ctrl.isAuth && ctrl.hasModeScores()) sideContent.push(h('div.box', scoreCharts(ctrl)));

  return h('div.side', sideContent);
};

const board = (ctrl: CoordinateTrainerCtrl): VNode => {
  return h('div.main-board', [
    ctrl.playing && ctrl.mode === 'findSquare'
      ? h(
          'svg.coords-svg',
          { attrs: { viewBox: '0 0 100 100' } },
          ['current', 'next'].map((modifier: CoordModifier) =>
            h(
              `g.${modifier}`,
              {
                key: `${ctrl.score}-${modifier}`,
                style:
                  modifier === 'current'
                    ? ({
                        remove: {
                          opacity: 0,
                          transform: 'translate(-8px, 60px)',
                        },
                      } as unknown as VNodeStyle)
                    : undefined,
              },
              h('text', modifier === 'current' ? ctrl.currentKey : ctrl.nextKey)
            )
          )
        )
      : null,
    chessground(ctrl),
  ]);
};

const explanation = (ctrl: CoordinateTrainerCtrl): VNode => {
  const { trans } = ctrl;
  return h('div.explanation', [
    h('p', trans('knowingTheChessBoard')),
    h('ul', [
      h('li', trans('mostChessCourses')),
      h('li', trans('talkToYourChessFriends')),
      h('li', trans('youCanAnalyseAGameMoreEffectively')),
    ]),
    h('strong', trans(ctrl.mode)),
    h('p', trans(ctrl.mode === 'findSquare' ? 'aSquareNameAppears' : 'aSquareIsHighlighted')),
  ]);
};

const table = (ctrl: CoordinateTrainerCtrl): VNode => {
  return h('div.table', [
    ctrl.hasPlayed ? null : explanation(ctrl),
    ctrl.playing
      ? null
      : h(
          'button.start.button.button-fat',
          {
            hook: bind('click', ctrl.start),
          },
          ctrl.trans('startTraining')
        ),
  ]);
};

const progress = (ctrl: CoordinateTrainerCtrl): VNode => {
  return h(
    'div.progress',
<<<<<<< HEAD
    ctrl.hasPlayed ? h('div.progress_bar', { style: { width: `${100 * (1 - ctrl.timeLeft / DURATION)}%` } }) : null
=======
    ctrl.hasPlayed ? h('div.progress__bar', { style: { width: `${100 * (1 - ctrl.timeLeft / DURATION)}%` } }) : null
>>>>>>> 42d37d2656 (remove one minute time option)
  );
};

const coordinateInput = (ctrl: CoordinateTrainerCtrl): MaybeVNode => {
  const coordinateInput = [
    h(
      'div.keyboard-container',
      {
        class: {
          hidden: ctrl.coordinateInputMethod === 'buttons',
        },
      },
      [
        h('input.keyboard', {
          hook: {
            insert: vnode => {
              ctrl.keyboardInput = vnode.elm as HTMLInputElement;
            },
          },
          on: { keyup: ctrl.onKeyboardInputKeyUp },
        }),
        ctrl.playing ? h('span', 'Enter the coordinate') : h('strong', 'Press <enter> to start'),
      ]
    ),
    ctrl.coordinateInputMethod === 'buttons'
      ? h(
          'div.files-ranks',
          'abcdefgh12345678'.split('').map((fileOrRank: string) =>
            h(
              'button.file-rank',
              {
                on: {
                  click: () => {
                    if (ctrl.playing) {
                      ctrl.keyboardInput.value += fileOrRank;
                      ctrl.checkKeyboardInput();
                    }
                  },
                },
              },
              fileOrRank
            )
          )
        )
      : null,
  ];
  const inputMethodSwitcher = ctrl.playing
    ? null
    : h(
        'a',
        { on: { click: () => ctrl.toggleInputMethod() } },
        ctrl.coordinateInputMethod === 'text' ? 'Use buttons instead' : 'Use keyboard instead'
      );
  return ctrl.mode === 'nameSquare' ? h('div.coordinate-input', [...coordinateInput, inputMethodSwitcher]) : null;
};

const view = (ctrl: CoordinateTrainerCtrl): VNode =>
  h(
    'div.trainer',
    {
      class: {
        wrong: ctrl.wrong,
      },
    },
    [side(ctrl), board(ctrl), table(ctrl), progress(ctrl), coordinateInput(ctrl)]
  );

export default view;
