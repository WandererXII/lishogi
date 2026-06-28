import { svgSprite } from 'common/icon-selector';
import { icons } from 'common/icons';
import { allLangs } from 'common/langs';
import { bind, dataIcon, type MaybeVNode, type MaybeVNodes } from 'common/snabbdom';
import { i18n, i18nPluralSame } from 'i18n';
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
import type { StudyCtrl, ToolTab } from './interfaces';
import { view as inviteFormView } from './invite-form';
import { view as multiBoardView } from './multi-board';
import { view as notifView } from './notif';
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
  return h('div.study__buttons', [
    h('div.left-buttons.tabs-horiz', [
      toolButton({
        ctrl,
        tab: 'study',
        hint: '',
        icon: iconTag(icons.study),
      }),
      toolButton({
        ctrl,
        tab: 'members',
        hint: '',
        icon: iconTag(icons.people),
      }),
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
    h('div.right', gbOverrideButton(ctrl)),
  ]);
}

function tags(ctrl: StudyCtrl): VNode {
  return h('div.study__tags', [tagsView(ctrl)]);
}

function info(ctrl: StudyCtrl): VNode {
  const icon = ctrl.data.icon || 'study';
  const owner = ctrl.members.owner()?.user || { name: 'lishogi' };

  return h('div.study__info', [
    h('div.study__title', { attrs: { title: ctrl.data.name } }, [
      h('span.icon', {
        key: icon,
        hook: {
          insert: vnode => {
            (vnode.elm as HTMLElement).innerHTML = svgSprite('study', icon);
          },
        },
      }),
      h('h1', ctrl.data.name),
      h('div.actions', [
        ctrl.members.isOwner()
          ? h(
              'span.action.more',
              {
                hook: bind('click', () => ctrl.form.open(!ctrl.form.open()), ctrl.redraw),
              },
              iconTag(icons.gear),
            )
          : undefined,
        h(
          'span.action.like.text',
          {
            class: { liked: ctrl.data.liked },
            attrs: {
              'data-icon': ctrl.data.liked ? icons.heartFull : icons.heartOutline,
              title: `${i18n('study:like')} (${ctrl.data.likes})`,
            },
            hook: bind('click', ctrl.toggleLike),
          },
          h('strong', ctrl.data.likes),
        ),
      ]),
    ]),
    chapterView(ctrl),
    h('div.study__meta', [
      h('ul.study__meta-info', [
        h(
          'li',
          h(
            'a.user-link.ulpt',
            {
              attrs: { href: `/@/${owner.name.toLowerCase()}` },
            },
            [owner.patron ? h('i.line.patron') : undefined, owner.name],
          ),
        ),
        h('li', i18nPluralSame('study:nbChapters', ctrl.data.chapters.length)),
        ctrl.data.lang ? h('li', allLangs[ctrl.data.lang] || ctrl.data.lang) : undefined,
        h(
          'li',
          h(
            'span.timeago',
            {
              attrs: {
                datetime: ctrl.data.createdAt,
                title: new Date(ctrl.data.createdAt).toLocaleString(),
              },
            },
            window.lishogi.timeago.format(ctrl.data.createdAt),
          ),
        ),
        ctrl.data.postGameStudy?.gameId
          ? h(
              'li',
              h(
                'a',
                {
                  attrs: {
                    target: '_blank',
                    href: `/${ctrl.data.postGameStudy.gameId}`,
                  },
                },
                ctrl.data.postGameStudy.gameId,
              ),
            )
          : undefined,
      ]),
      ctrl.data.description
        ? h('div.study__meta-desc', [h('p', ctrl.data.description)])
        : undefined,
    ]),
    topicsView(ctrl),
  ]);
}

export function side(ctrl: StudyCtrl): VNode {
  return h('div.study__side', [h('div.chapter__side', chapterView(ctrl)), modes(ctrl)]);
}

function showModeRec(ctrl: StudyCtrl): boolean {
  return ctrl.members.canContribute();
}

function showSyncRec(ctrl: StudyCtrl): boolean {
  return ctrl.data.features.sticky && (ctrl.members.canContribute() || ctrl.isUpdatedRecently());
}

function modes(ctrl: StudyCtrl): MaybeVNode {
  const canContribute = ctrl.members.canContribute();

  const showRecord = showModeRec(ctrl);
  const showSticky = showSyncRec(ctrl);

  if (!showRecord && !showSticky) return;

  return h('div.study__actions', [
    showSticky
      ? h(
          'div.mode',

          {
            attrs: {
              title: i18n('study:allSyncMembersRemainOnTheSamePosition'),
              'data-count': ctrl.vm.behind ? ctrl.vm.behind : false,
            },
            class: { on: ctrl.vm.mode.sticky },
            hook: bind('click', ctrl.toggleSticky),
          },
          'SYNC',
        )
      : undefined,
    canContribute
      ? h(
          'div.mode',
          {
            attrs: { title: i18n('study:shareChanges') },
            class: { on: ctrl.vm.mode.write },
            hook: bind('click', ctrl.toggleWrite),
          },
          'REC',
        )
      : undefined,
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
  const study = ctrl.study!;
  const toolTab = study.vm.toolTab();
  if (study.gamebookPlay()) return [descView(study), gbPlayButtons(ctrl), info(study)];
  let panel: VNode | undefined;
  switch (toolTab) {
    case 'study':
      panel = info(study);
      break;
    case 'members':
      panel = memberView(study);
      break;
    case 'tags':
      panel = tags(study);
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
  return [notifView(study.notif), descView(study), buttons(ctrl), panel];
}
