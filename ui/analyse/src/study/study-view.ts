import { svgSprite } from 'common/icon-selector';
import { icons } from 'common/icons';
import { bind, dataIcon, type MaybeVNode, type MaybeVNodes } from 'common/snabbdom';
import { i18n, i18nPluralSame } from 'i18n';
import { opposite } from 'shogiground/util';
import { h, type VNode } from 'snabbdom';
import type AnalyseCtrl from '../ctrl';
import { iconTag } from '../util';
import { view as chapterEditFormView } from './chapter-edit-form';
import { view as chapterNewFormView } from './chapter-new-form';
import * as commentForm from './comment-form';
import { view as descView } from './description';
import {
  overrideButton as gbOverrideButton,
  playButtons as gbPlayButtons,
} from './gamebook/gamebook-buttons';
import type { StudyCtrl, Tab, ToolTab } from './interfaces';
import { view as inviteFormView } from './invite-form';
import { view as multiBoardView } from './multi-board';
import { view as notifView } from './notif';
import * as practiceView from './practice/study-practice-view';
import { view as serverEvalView } from './server-eval';
import { view as chapterView } from './study-chapters';
import { view as studyFormView } from './study-form';
import * as glyphForm from './study-glyph';
import { view as memberView } from './study-members';
import { view as studyShareView } from './study-share';
import { view as tagsView } from './study-tags';
import { formView as topicsFormView, view as topicsView } from './topics';

interface ToolButtonOpts {
  ctrl: StudyCtrl;
  tab: ToolTab;
  hint: string;
  icon: VNode;
  onClick?: () => void;
  count?: number | string;
}

function toolButton(opts: ToolButtonOpts): VNode {
  return h(
    `span.${opts.tab}`,
    {
      attrs: { title: opts.hint },
      class: { active: opts.tab === opts.ctrl.vm.toolTab() },
      hook: bind(
        'mousedown',
        () => {
          if (opts.onClick) opts.onClick();
          opts.ctrl.vm.toolTab(opts.tab);
        },
        opts.ctrl.redraw,
      ),
    },
    [opts.count ? h('count.data-count', { attrs: { 'data-count': opts.count } }) : null, opts.icon],
  );
}

function buttons(root: AnalyseCtrl): VNode {
  const ctrl: StudyCtrl = root.study!;
  const canContribute = ctrl.members.canContribute();
  const showSticky =
    ctrl.data.features.sticky && (canContribute || (ctrl.vm.behind && ctrl.isUpdatedRecently()));
  return h('div.study__buttons', [
    h('div.left-buttons.tabs-horiz', [
      // distinct classes (sync, write) allow snabbdom to differentiate buttons
      showSticky
        ? h(
            'a.mode.sync',
            {
              attrs: { title: i18n('study:allSyncMembersRemainOnTheSamePosition') },
              class: { on: ctrl.vm.mode.sticky },
              hook: bind('click', ctrl.toggleSticky),
            },
            [ctrl.vm.behind ? h('span.behind', `${ctrl.vm.behind}`) : h('i.is'), 'SYNC'],
          )
        : null,
      canContribute
        ? h(
            'a.mode.write',
            {
              attrs: { title: i18n('study:shareChanges') },
              class: { on: ctrl.vm.mode.write },
              hook: bind('click', ctrl.toggleWrite),
            },
            [h('i.is'), 'REC'],
          )
        : null,
      toolButton({
        ctrl,
        tab: 'tags',
        hint: i18n('study:tags'),
        icon: iconTag(icons.tag),
      }),
      toolButton({
        ctrl,
        tab: 'comments',
        hint: i18n('study:commentThisPosition'),
        icon: iconTag(icons.talk),
        onClick() {
          ctrl.commentForm.start(ctrl.vm.chapterId, root.path, root.node);
        },
        count: (root.node.comments || []).length,
      }),
      canContribute
        ? toolButton({
            ctrl,
            tab: 'glyphs',
            hint: i18n('study:annotateWithGlyphs'),
            // random data icon for proper positioning
            icon: h('i.glyph-icon', { attrs: { 'data-icon': icons.warning } }),
            count: (root.node.glyphs || []).length,
          })
        : null,
      toolButton({
        ctrl,
        tab: 'serverEval',
        hint: i18n('computerAnalysis'),
        icon: iconTag(icons.barChart),
        count: root.data.analysis && '•',
      }),
      toolButton({
        ctrl,
        tab: 'multiBoard',
        hint: 'Multiboard',
        icon: iconTag(icons.table),
      }),
      toolButton({
        ctrl,
        tab: 'share',
        hint: i18n('study:shareAndExport'),
        icon: iconTag(icons.share),
      }),
    ]),
    h('div.right', [gbOverrideButton(ctrl)]),
  ]);
}

function postGameButtons(ctrl: StudyCtrl): MaybeVNode {
  if (ctrl.data.postGameStudy) {
    const usersGameColor = (userId: string): Color | undefined => {
      return ctrl.data.postGameStudy?.players.sente.userId === userId
        ? 'sente'
        : ctrl.data.postGameStudy?.players.gote.userId === userId
          ? 'gote'
          : undefined;
    };
    const userId = document.body.dataset.user;
    const myColor = userId ? usersGameColor(userId) : undefined;
    const me = myColor && ctrl.data.postGameStudy.players[myColor];
    const gameBackButton = h(
      `a.button.button-empty${!me ? '.text' : ''}`,
      {
        attrs: {
          title: i18n('backToGame'),
          href: `/${ctrl.data.postGameStudy.gameId}`,
          ...dataIcon(icons.back),
        },
      },
      !me ? i18n('backToGame') : null,
    );
    if (me) {
      const myOpponent = ctrl.data.postGameStudy.players[opposite(myColor)];
      const isOnline = myOpponent.userId && ctrl.members.isOnline(myOpponent.userId);
      const offering = !!ctrl.data.postGameStudy.rematches[myColor];
      const offered = !!ctrl.data.postGameStudy.rematches[opposite(myColor)];
      return h('div.game_info', [
        gameBackButton,
        h(
          `button.button.button-empty${!isOnline || !myOpponent.userId ? '.disabled' : ''}`,
          {
            class: {
              offering: offering,
              glowing: offered,
            },
            attrs: {
              title: offering
                ? i18n('cancel')
                : myOpponent.userId
                  ? `${i18n('rematch')} ${myOpponent.userId}`
                  : 'Cannot rematch anonymous player in study',
              ...dataIcon(icons.challenge),
            },
            hook: bind('click', () => {
              ctrl.rematch(!ctrl.data.postGameStudy?.rematches[myColor]);
            }),
          },
          offering ? h('span', { attrs: dataIcon(icons.cancel) }) : i18n('rematch'),
        ),
      ]);
    } else return h('div.game_info', gameBackButton);
  } else return null;
}

function metadata(ctrl: StudyCtrl): VNode {
  const d = ctrl.data;
  const title = `${d.name}: ${ctrl.currentChapter().name}`;
  return h('div.study__metadata', [
    h('h2', [
      h('div.name', [
        h('span.icon', {
          key: d.icon,
          hook: {
            insert: vnode => {
              (vnode.elm as HTMLElement).innerHTML = d.icon ? svgSprite('study', d.icon) : '';
            },
          },
        }),
        h('span', { attrs: { title } }, title),
      ]),
      h(
        'span.liking.text',
        {
          class: { liked: d.liked },
          attrs: {
            'data-icon': d.liked ? icons.heartFull : icons.heartOutline,
            title: i18n('study:like'),
          },
          hook: bind('click', ctrl.toggleLike),
        },
        `${d.likes}`,
      ),
    ]),
    topicsView(ctrl),
    tagsView(ctrl),
  ]);
}

export function side(ctrl: StudyCtrl): VNode {
  const activeTab = ctrl.vm.tab();

  const makeTab = (key: Tab, name: string) =>
    h(
      `span.${key}`,
      {
        class: { active: activeTab === key },
        hook: bind(
          'mousedown',
          () => {
            ctrl.vm.tab(key);
          },
          ctrl.redraw,
        ),
      },
      name,
    );

  const tabs = h('div.tabs-horiz', [
    makeTab('chapters', i18nPluralSame('study:nbChapters', ctrl.chapters.size())),
    makeTab('members', i18nPluralSame('study:nbMembers', ctrl.members.size())),
    ctrl.members.isOwner()
      ? h(
          'span.more',
          {
            hook: bind('click', () => ctrl.form.open(!ctrl.form.open()), ctrl.redraw),
          },
          [iconTag(icons.gear)],
        )
      : null,
  ]);

  return h('div.study__side', [
    tabs,
    (activeTab === 'members' ? memberView : chapterView)(ctrl),
    postGameButtons(ctrl),
  ]);
}

export function contextMenu(ctrl: StudyCtrl, path: Tree.Path, node: Tree.Node): VNode[] {
  return ctrl.vm.mode.write
    ? [
        h(
          'a',
          {
            attrs: dataIcon(icons.talk),
            hook: bind('click', () => {
              ctrl.vm.toolTab('comments');
              ctrl.commentForm.start(ctrl.currentChapter()!.id, path, node);
            }),
          },
          i18n('study:commentThisMove'),
        ),
        h(
          'a.glyph-icon',
          {
            hook: bind('click', () => {
              ctrl.vm.toolTab('glyphs');
              ctrl.userJump(path);
            }),
          },
          i18n('study:annotateWithGlyphs'),
        ),
      ]
    : [];
}

export function overboard(ctrl: StudyCtrl): MaybeVNode {
  if (ctrl.chapters.newForm.vm.open) return chapterNewFormView(ctrl.chapters.newForm);
  if (ctrl.chapters.editForm.current()) return chapterEditFormView(ctrl.chapters.editForm);
  if (ctrl.members.inviteForm.open()) return inviteFormView(ctrl.members.inviteForm);
  if (ctrl.topics.open()) return topicsFormView(ctrl.topics, ctrl.members.myId);
  if (ctrl.form.open()) return studyFormView(ctrl.form);
  return undefined;
}

export function underboard(ctrl: AnalyseCtrl): MaybeVNodes {
  if (ctrl.embed) return [];
  if (ctrl.studyPractice) return practiceView.underboard(ctrl.study!);
  const study = ctrl.study!;
  const toolTab = study.vm.toolTab();
  if (study.gamebookPlay())
    return [descView(study, true), descView(study, false), gbPlayButtons(ctrl), metadata(study)];
  let panel: VNode | undefined;
  switch (toolTab) {
    case 'tags':
      panel = metadata(study);
      break;
    case 'comments':
      panel = study.vm.mode.write
        ? commentForm.view(ctrl)
        : commentForm.viewDisabled(
            ctrl,
            study.members.canContribute()
              ? 'Press REC to comment moves'
              : 'Only the study members can comment on moves',
          );
      break;
    case 'glyphs':
      panel = ctrl.path
        ? study.vm.mode.write
          ? glyphForm.view(study.glyphForm)
          : glyphForm.viewDisabled('Press REC to annotate moves')
        : glyphForm.viewDisabled('Select a move to annotate');
      break;
    case 'serverEval':
      panel = serverEvalView(study.serverEval);
      break;
    case 'share':
      panel = studyShareView(study.share);
      break;
    case 'multiBoard':
      panel = multiBoardView(study.multiBoard, study);
      break;
  }
  return [
    notifView(study.notif),
    descView(study, true),
    descView(study, false),
    buttons(ctrl),
    panel,
  ];
}
