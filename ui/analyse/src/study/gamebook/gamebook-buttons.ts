import { bind, dataIcon } from 'common/snabbdom';
import { i18n } from 'i18n';
import { h, type VNode } from 'snabbdom';
import type AnalyseCtrl from '../../ctrl';
import type { StudyCtrl } from '../interfaces';

export function playButtons(root: AnalyseCtrl): VNode | undefined {
  const study = root.study!;
  const ctrl = study.gamebookPlay();
  if (!ctrl) return;
  const state = ctrl.state;
  const fb = state.feedback;
  const myTurn = fb === 'play';
  return h(
    'div.study__buttons.gamebook-buttons',
    h('div.right', [
      h(
        'div.text.back',
        {
          attrs: { 'data-icon': 'I', disabled: !root.path },
          hook: bind('click', () => root.userJump(''), ctrl.redraw),
        },
        i18n('back'),
      ),
      h(
        'div.text.solution',
        {
          attrs: { 'data-icon': 'G', disabled: !myTurn },
          hook: bind('click', ctrl.solution, ctrl.redraw),
        },
        i18n('viewTheSolution'),
      ),
      overrideButton(study),
    ]),
  );
}

export function overrideButton(study: StudyCtrl): VNode | undefined {
  if (study.data.chapter.gamebook) {
    const o = study.vm.gamebookOverride;
    if (study.members.canContribute())
      return h(
        'a.fbt.text.preview',
        {
          class: { active: o === 'play' },
          attrs: dataIcon('v'),
          hook: bind(
            'click',
            () => {
              study.setGamebookOverride(o === 'play' ? undefined : 'play');
            },
            study.redraw,
          ),
        },
        i18n('preview'),
      );
    else {
      const isAnalyse = o === 'analyse';
      const ctrl = study.gamebookPlay();
      if (isAnalyse || (ctrl && ctrl.state.feedback === 'end'))
        return h(
          'a.fbt.text.preview',
          {
            class: { active: isAnalyse },
            attrs: dataIcon('A'),
            hook: bind(
              'click',
              () => {
                study.setGamebookOverride(isAnalyse ? undefined : 'analyse');
              },
              study.redraw,
            ),
          },
          i18n('analyse'),
        );
    }
  }
  return undefined;
}
