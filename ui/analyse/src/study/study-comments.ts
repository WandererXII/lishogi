import { richHTML } from 'common/rich-text';
import { bind } from 'common/snabbdom';
import { i18n } from 'i18n';
import { h, type VNode } from 'snabbdom';
import type AnalyseCtrl from '../ctrl';
import { nodeFullName } from '../util';
import type { StudyCtrl } from './interfaces';

function authorDom(author: Tree.CommentAuthor) {
  if (!author) return 'Unknown';
  if (typeof author === 'string') return author;
  else
    return h(
      'span.user-link.ulpt',
      {
        attrs: { 'data-href': `/@/${author.id}` },
      },
      author.name,
    );
}

export function authorText(author: Tree.CommentAuthor): string {
  if (!author) return '';
  if (typeof author === 'string') return `[${author}]`;
  else return `[${author.name}]`;
}

export function currentComments(ctrl: AnalyseCtrl, includingMine: boolean): VNode | undefined {
  if (!ctrl.node.comments) return;
  const node = ctrl.node;
  const study: StudyCtrl = ctrl.study!;
  const chapter = study.currentChapter();
  const comments = node.comments!;
  if (!comments.length) return;
  return h(
    'div',
    comments.map((comment: Tree.Comment) => {
      const by: any = comment.by;
      const isMine = by.id && ctrl.opts.userId === by.id;
      if (!includingMine && isMine) return;
      const canDelete = isMine || study.members.isOwner();
      return h(`div.study__comment.${comment.id}`, [
        canDelete && study.vm.mode.write
          ? h('a.edit', {
              attrs: {
                'data-icon': 'q',
                title: i18n('delete'),
              },
              hook: bind(
                'click',
                _ => {
                  if (confirm(`Delete ${authorText(by)}'s comment?`))
                    study.commentForm.delete(chapter.id, ctrl.path, comment.id);
                },
                ctrl.redraw,
              ),
            })
          : null,
        authorDom(by),
        ...(node.usi ? [' on ', h('span.node', nodeFullName(node))] : []),
        ': ',
        h('div.text', { hook: richHTML(comment.text) }),
      ]);
    }),
  );
}
