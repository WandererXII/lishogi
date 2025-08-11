import { i18n } from 'i18n';
import { h, type VNode } from 'snabbdom';
import type LearnCtrl from '../ctrl';
import type { Level } from '../interfaces';
import congrats from './congrats';
import completed from './overlays/completed';
import starting from './overlays/starting';
import side from './side';

const star = h('i', { attrs: { 'data-icon': 't' } });

function makeStars(score: number): VNode {
  const stars = [];
  for (let i = 0; i < score; i++) stars.push(star);
  return h(`span.stars.st${stars.length}`, stars);
}

function progress(ctrl: LearnCtrl) {
  const vm = ctrl.vm!;
  return h(
    'div.progress',
    vm.stage.levels.map((level: Level) => {
      const score = ctrl.progress.get(vm.stage.key)[level.id - 1];
      const status = level.id === vm.level.id ? 'active' : score ? 'done' : 'future';
      const label = score ? makeStars(score) : h('span.id', level.id);
      return h(
        `a.${status}`,
        {
          on: {
            click: () => {
              ctrl.setLesson(vm.stage.id, level.id);
              ctrl.redraw();
            },
          },
        },
        label,
      );
    }),
  );
}

function renderFailed(ctrl: LearnCtrl) {
  return h(
    'div.result.failed',
    {
      on: {
        click: () => {
          ctrl.restartLevel();
          ctrl.redraw();
        },
      },
    },
    [
      h('h2', i18n('learn:puzzleFailed')),
      h(
        'button',
        {
          on: {
            click: () => {
              ctrl.restartLevel();
              ctrl.redraw();
            },
          },
        },
        i18n('learn:retry'),
      ),
    ],
  );
}

function renderCompleted(ctrl: LearnCtrl) {
  const vm = ctrl.vm!;
  return h(
    'div.result.completed',
    {
      on: {
        click: () => {
          ctrl.nextLesson();
          ctrl.redraw();
        },
      },
    },
    [
      h('h2', congrats()),
      vm.level.nextButton || vm.stageState === 'end'
        ? h(
            'button',
            {
              on: {
                click: () => {
                  ctrl.completeLevel();
                  ctrl.redraw();
                },
              },
            },
            i18n('next'),
          )
        : makeStars(vm.score || 0),
    ],
  );
}

function renderInfo(ctrl: LearnCtrl) {
  if (!ctrl.vm?.level.text) return null;
  return h(
    'a.info-text',
    {
      on: {
        click: e => {
          e.stopPropagation();
          ctrl.completeLevel();
        },
      },
    },
    ctrl.vm.level.text,
  );
}

function shogigroundBoard(ctrl: LearnCtrl): VNode {
  return h('div.sg-wrap', {
    hook: {
      insert: (vnode: VNode) => {
        ctrl.shogiground.attach({ board: vnode.elm as HTMLElement });
      },
      destroy: () => {
        ctrl.shogiground.detach({ board: true });
      },
    },
  });
}

export default function (ctrl: LearnCtrl): VNode {
  const vm = ctrl.vm!;
  const stage = vm.stage;
  const level = vm.level;
  const stageStarting = vm.stageState === 'init';
  const stageEnding = vm.stageState === 'completed';

  return h(
    'div.main.learn learn--run',
    {
      class: {
        starting: stageStarting,
        completed: stageEnding && !level.nextButton,
      },
    },
    [
      h('div.learn__side', side(ctrl)),
      h('div.learn__main.main-board', [
        stageStarting ? starting(ctrl) : null,
        stageEnding ? completed(ctrl) : null,
        shogigroundBoard(ctrl),
        renderInfo(ctrl),
      ]),
      h('div.learn__table', [
        h('div.wrap', [
          h('div.title', [h('h2', stage.title), h('p.subtitle', stage.subtitle)]),
          vm.levelState === 'fail'
            ? renderFailed(ctrl)
            : vm.levelState === 'completed'
              ? renderCompleted(ctrl)
              : h('div.goal', level.goal),
          progress(ctrl),
        ]),
      ]),
    ],
  );
}
