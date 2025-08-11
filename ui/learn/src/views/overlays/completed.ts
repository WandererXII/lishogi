import type { MaybeVNode } from 'common/snabbdom';
import { i18n, i18nFormat } from 'i18n';
import { h, type VNode } from 'snabbdom';
import { nextStage } from '../../categories';
import type LearnCtrl from '../../ctrl';
import { average } from '../../util';

function makeStars(score: number): VNode[] {
  const stars = [];
  for (let i = 0; i < score; i++) stars.push(h('div.star-wrap', h('i.star')));
  return stars;
}

export default function (ctrl: LearnCtrl): MaybeVNode {
  if (!ctrl.vm) return;
  const stage = ctrl.vm.stage;
  const next = nextStage(stage.id);
  const stars = Math.floor(average(ctrl.progress.get(stage.key)));
  return h(
    'div.learn__screen-overlay.completed',
    {
      on: {
        click: () => {
          if (ctrl.vm) ctrl.vm.stageState = 'end';
          ctrl.redraw();
        },
      },
    },
    h('div.learn__screen', [
      h('div.stars', makeStars(stars)),
      h('h1', i18nFormat('learn:stageXComplete', stage.id)),
      h('p', stage.complete),
      h('div.buttons', [
        next
          ? h(
              'button.button',
              {
                on: {
                  click: () => {
                    ctrl.nextLesson();
                    ctrl.redraw();
                  },
                },
              },
              [`${i18n('learn:next')}: `, `${next.title} `],
            )
          : null,
        h(
          'button.button.button-red',
          {
            on: {
              click: () => {
                ctrl.setHome();
                ctrl.redraw();
              },
            },
          },
          i18n('learn:backToMenu'),
        ),
      ]),
    ]),
  );
}
