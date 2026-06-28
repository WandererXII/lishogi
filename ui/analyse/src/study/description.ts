import { icons } from 'common/icons';
import { richHTML } from 'common/rich-text';
import { bind, onInsert } from 'common/snabbdom';
import { h, type VNode } from 'snabbdom';
import type { StudyCtrl } from './interfaces';

type Save = (t: string) => void;

export class DescriptionCtrl {
  edit = false;

  constructor(
    public text: string | undefined,
    readonly doSave: Save,
    readonly redraw: () => void,
  ) {}

  save(t: string): void {
    this.text = t;
    this.doSave(t);
    this.redraw();
  }

  set(t: string | undefined): void {
    this.text = t ? t : undefined;
  }
}

function descTitle() {
  return 'Chapter pinned comment';
}

export function view(study: StudyCtrl): VNode | undefined {
  const desc = study.chapterDesc;
  const contrib = study.members.canContribute() && !study.gamebookPlay();
  if (desc.edit) return edit(desc, study.data.chapter.id);
  const isEmpty = desc.text === '-';
  if (!desc.text || (isEmpty && !contrib)) return;
  return h(`div.study-desc.chapter-desc${isEmpty ? '.empty' : ''}`, [
    contrib && !isEmpty
      ? h('div.contrib', [
          h('span', descTitle()),
          isEmpty
            ? null
            : h('a', {
                attrs: {
                  'data-icon': icons.pencil,
                  title: 'Edit',
                },
                hook: bind(
                  'click',
                  _ => {
                    desc.edit = true;
                  },
                  desc.redraw,
                ),
              }),
          h('a', {
            attrs: {
              'data-icon': icons.trashBin,
              title: 'Delete',
            },
            hook: bind('click', () => {
              if (confirm('Delete permanent description?')) desc.save('');
            }),
          }),
        ])
      : null,
    isEmpty
      ? h(
          'a.text.button',
          {
            hook: bind(
              'click',
              _ => {
                desc.edit = true;
              },
              desc.redraw,
            ),
          },
          descTitle(),
        )
      : h('div.text', { hook: richHTML(desc.text) }),
  ]);
}

function edit(ctrl: DescriptionCtrl, id: string): VNode {
  return h('div.study-desc-form', [
    h('div.title', [
      descTitle(),
      h('button.button.button-empty.button-red', {
        attrs: {
          'data-icon': icons.cancel,
          title: 'Close',
        },
        hook: bind(
          'click',
          () => {
            ctrl.edit = false;
          },
          ctrl.redraw,
        ),
      }),
    ]),
    h('form.form3', [
      h('div.form-group', [
        h(`textarea#form-control.desc-text.${id}`, {
          hook: onInsert<HTMLInputElement>(el => {
            el.value = ctrl.text === '-' ? '' : ctrl.text || '';
            el.onkeyup = el.onpaste = () => {
              ctrl.save(el.value.trim());
            };
            el.focus();
          }),
        }),
      ]),
    ]),
  ]);
}
