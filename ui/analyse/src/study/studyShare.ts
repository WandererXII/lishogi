import { Prop, prop } from 'common/common';
import { bind } from 'common/snabbdom';
import { VNode, h } from 'snabbdom';
import { renderIndexAndMove } from '../moveView';
import { baseUrl } from '../util';
import { StudyChapterMeta, StudyData } from './interfaces';

interface StudyShareCtrl {
  studyId: string;
  chapter: () => StudyChapterMeta;
  isPrivate(): boolean;
  currentNode: () => Tree.Node;
  withPly: Prop<boolean>;
  relay: boolean;
  cloneable: boolean;
  notation: number;
  offset: number;
  redraw: () => void;
  trans: Trans;
}

function fromPly(ctrl: StudyShareCtrl): VNode {
  const renderedMove = renderIndexAndMove(
    {
      notation: ctrl.notation,
      variant: ctrl.chapter().variant,
      withDots: true,
      showEval: false,
      offset: ctrl.offset,
    },
    ctrl.currentNode()
  );
  return h(
    'div.ply-wrap',
    h('label.ply', [
      h('input', {
        attrs: { type: 'checkbox' },
        hook: bind(
          'change',
          e => {
            ctrl.withPly((e.target as HTMLInputElement).checked);
          },
          ctrl.redraw
        ),
      }),
      ...(renderedMove
        ? ctrl.trans.vdom('startAtX', h('strong', renderedMove))
        : [ctrl.trans.noarg('startAtInitialPosition')]),
    ])
  );
}

export function ctrl(
  data: StudyData,
  currentChapter: () => StudyChapterMeta,
  currentNode: () => Tree.Node,
  relay: boolean,
  redraw: () => void,
  notation: number,
  offset: number,
  trans: Trans
): StudyShareCtrl {
  const withPly = prop(false);
  return {
    studyId: data.id,
    chapter: currentChapter,
    isPrivate() {
      return data.visibility === 'private';
    },
    currentNode,
    withPly,
    relay,
    cloneable: data.features.cloneable,
    notation: notation,
    redraw,
    trans,
    offset,
  };
}

export function view(ctrl: StudyShareCtrl): VNode {
  const studyId = ctrl.studyId,
    chapter = ctrl.chapter();
  let fullUrl = `${baseUrl()}/study/${studyId}/${chapter.id}`;
  let embedUrl = `${baseUrl()}/study/embed/${studyId}/${chapter.id}`;
  const isPrivate = ctrl.isPrivate();
  if (ctrl.withPly()) {
    const p = ctrl.currentNode().ply;
    fullUrl += '#' + p;
    embedUrl += '#' + p;
  }
  return h('div.study__share', [
    h('form.form3', [
      h('div.form-group', [
        h('label.form-label', ctrl.trans.noarg(ctrl.relay ? 'broadcastUrl' : 'studyUrl')),
        h('input.form-control.autoselect', {
          attrs: {
            readonly: true,
            value: `${baseUrl()}/study/${studyId}`,
          },
        }),
      ]),
      h('div.form-group', [
        h('label.form-label', ctrl.trans.noarg(ctrl.relay ? 'currentGameUrl' : 'currentChapterUrl')),
        h('input.form-control.autoselect', {
          attrs: {
            readonly: true,
            value: fullUrl,
          },
        }),
        fromPly(ctrl),
        !isPrivate
          ? h(
              'p.form-help.text',
              {
                attrs: { 'data-icon': '' },
              },
              ctrl.trans.noarg('youCanPasteThisInTheForumToEmbed')
            )
          : null,
      ]),
      h(
        'div.form-group',
        [
          h('label.form-label', ctrl.trans.noarg('embedInYourWebsite')),
          h('input.form-control.autoselect', {
            attrs: {
              readonly: true,
              disabled: isPrivate,
              value: !isPrivate
                ? `<iframe width=600 height=371 src="${embedUrl}" frameborder=0></iframe>`
                : ctrl.trans.noarg('onlyPublicStudiesCanBeEmbedded'),
            },
          }),
        ].concat(
          !isPrivate
            ? [
                fromPly(ctrl),
                h(
                  'a.form-help.text',
                  {
                    attrs: {
                      href: '/developers#embed-study',
                      target: '_blank',
                      'data-icon': '',
                    },
                  },
                  ctrl.trans.noarg('readMoreAboutEmbedding')
                ),
              ]
            : []
        )
      ),
      h('div.form-group', [
        h('label.form-label', 'SFEN'),
        h('input.form-control.autoselect', {
          attrs: {
            readonly: true,
            value: ctrl.currentNode().sfen,
          },
        }),
      ]),
    ]),
    h('div.downloads', [
      h(
        'a.button.text',
        {
          attrs: {
            'data-icon': 'x',
            href: `/study/${studyId}/${chapter.id}.kif`,
            download: true,
          },
        },
        ctrl.trans.noarg(ctrl.relay ? 'downloadGame' : 'chapterKif')
      ),
      'standard' === chapter.variant
        ? h(
            'a.button.text',
            {
              attrs: {
                'data-icon': 'x',
                href: `/study/${studyId}/${chapter.id}.csa`,
                download: true,
              },
            },
            ctrl.trans.noarg(ctrl.relay ? 'downloadGame' : 'chapterCsa')
          )
        : null,
      ctrl.cloneable
        ? h(
            'a.button.text',
            {
              attrs: {
                'data-icon': '4',
                href: `/study/${studyId}/clone`,
              },
            },
            ctrl.trans.noarg('cloneStudy')
          )
        : null,
      'standard' === chapter.variant
        ? h(
            'a.button.text',
            {
              attrs: {
                'data-icon': 'x',
                href: `/study/${studyId}/${chapter.id}.gif`,
                download: true,
              },
            },
            'GIF'
          )
        : null,
    ]),
  ]);
}
