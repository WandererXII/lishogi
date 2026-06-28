import { type Prop, prop } from 'common/common';
import { createIconSelector, svgSprite } from 'common/icon-selector';
import { allLangs } from 'common/langs';
import { modal } from 'common/modal';
import { bind, bindNonPassive, bindSubmit, type MaybeVNodes } from 'common/snabbdom';
import { i18n } from 'i18n';
import { h, type VNode } from 'snabbdom';
import { emptyRedButton } from '../util';
import type { StudyData } from './interfaces';
import { formButton } from './util';

export interface StudyFormCtrl {
  open: Prop<boolean>;
  openIfNew(): void;
  save(data: FormData, isNew: boolean): void;
  getData(): StudyData;
  isNew(): boolean;
  redraw: Redraw;
}

interface FormData {
  [key: string]: string;
}

interface Select {
  key: string;
  name: string;
  choices: (Choice | undefined)[];
  selected: string;
}
type Choice = [string, string];

function select(s: Select): MaybeVNodes {
  return [
    h(
      'label.form-label',
      {
        attrs: { for: `study-${s.key}` },
      },
      s.name,
    ),
    h(
      `select#study-${s.key}.form-control`,
      s.choices.map(o =>
        o
          ? h(
              'option',
              {
                attrs: {
                  value: o[0],
                  selected: s.selected === o[0],
                },
              },
              o[1],
            )
          : undefined,
      ),
    ),
  ];
}

export function ctrl(
  save: (data: FormData, isNew: boolean) => void,
  getData: () => StudyData,
  redraw: Redraw,
): StudyFormCtrl {
  const initAt = Date.now();

  function isNew(): boolean {
    const d = getData();
    return d.from === 'scratch' && !!d.isNew && Date.now() - initAt < 9000;
  }

  const open = prop(false);

  return {
    open,
    openIfNew() {
      if (isNew()) open(true);
    },
    save(data: FormData, isNew: boolean) {
      save(data, isNew);
      open(false);
    },
    getData,
    isNew,
    redraw,
  };
}

let iconSelector: ReturnType<typeof createIconSelector> | undefined;
const clearIconSelector = () => {
  if (iconSelector) {
    iconSelector.destroy();
    iconSelector = undefined;
  }
};

export function view(ctrl: StudyFormCtrl): VNode {
  const data = ctrl.getData();
  const isNew = ctrl.isNew();
  const icon = iconSelector?.selected() || data.icon || 'study';
  const userLangCode = document.documentElement.lang;
  const userLang = allLangs[userLangCode];

  const updateName = (vnode: VNode, isUpdate: boolean) => {
    const el = vnode.elm as HTMLInputElement;
    if (!isUpdate && !el.value) {
      el.value = data.name;
      if (isNew) el.select();
      el.focus();
    }
  };
  const userSelectionChoices: Choice[] = [
    ['nobody', i18n('study:nobody')],
    ['owner', i18n('study:onlyMe')],
    ['contributor', i18n('study:contributors')],
    ['member', i18n('study:members')],
    ['everyone', i18n('study:everyone')],
  ];

  return modal({
    class: 'study__modal.study-edit',
    onClose() {
      clearIconSelector();
      ctrl.open(false);
      ctrl.redraw();
    },
    content: [
      h('h2', isNew ? i18n('study:createStudy') : i18n('study:editStudy')),
      h(
        'form.form3',
        {
          hook: bindSubmit(e => {
            const obj: FormData = {};
            [
              'name',
              'lang',
              'visibility',
              'computer',
              'cloneable',
              'chat',
              'sticky',
              'description',
              'icon',
            ].forEach(n => {
              const el = (e.target as HTMLElement).querySelector(`#study-${n}`) as HTMLInputElement;
              if (el) obj[n] = el.value;
            });
            clearIconSelector();
            ctrl.save(obj, isNew);
          }, ctrl.redraw),
        },
        [
          h('div.form-split.icon-name', [
            h('div.form-group', [
              h('label.form-label', { attrs: { for: 'study-icon' } }, i18n('icon')),
              h('input#study-icon.none', {
                hook: {
                  insert: vnode => {
                    if (data.icon) (vnode.elm as HTMLInputElement).value = data.icon;
                  },
                },
              }),
              h(
                'div#icon-selector.icon-select',
                {
                  hook: bind('click', () => {
                    if (iconSelector) iconSelector.toggle();
                    else
                      iconSelector = createIconSelector(
                        document.getElementById('icon-selector')!,
                        data.icon || 'study',
                        'study',
                        key => {
                          const input = document.getElementById('study-icon') as HTMLInputElement;
                          input.value = key;
                          ctrl.redraw();
                        },
                      );
                  }),
                },
                h('div.icon', {
                  key: icon,
                  hook: {
                    insert: vnode => {
                      (vnode.elm as HTMLElement).innerHTML = svgSprite('study', icon);
                    },
                  },
                }),
              ),
            ]),
            h('div.form-group', [
              h('label.form-label', { attrs: { for: 'study-name' } }, i18n('name')),
              h('input#study-name.form-control', {
                attrs: {
                  minlength: 3,
                  maxlength: 100,
                },
                hook: {
                  insert: vnode => updateName(vnode, false),
                  postpatch: (_, vnode) => updateName(vnode, true),
                },
              }),
            ]),
          ]),
          h('div.form-split', [
            h(
              'div.form-group.form-half',
              select({
                key: 'lang',
                name: i18n('language'),
                choices: [
                  ['', ''],
                  userLang ? [userLangCode, userLang] : undefined,
                  ...Object.entries(allLangs),
                ],
                selected: data.lang || '',
              }),
            ),
            h(
              'div.form-group.form-half',
              select({
                key: 'visibility',
                name: i18n('study:visibility'),
                choices: [
                  ['public', i18n('study:public')],
                  ['unlisted', i18n('study:unlisted')],
                  ['private', i18n('study:inviteOnly')],
                ],
                selected: data.visibility,
              }),
            ),
          ]),
          h('div.form-split', [
            h(
              'div.form-group.form-half',
              select({
                key: 'cloneable',
                name: i18n('study:allowCloning'),
                choices: userSelectionChoices,
                selected: data.settings.cloneable,
              }),
            ),
            h(
              'div.form-group.form-half',
              select({
                key: 'computer',
                name: i18n('computerAnalysis'),
                choices: userSelectionChoices.map(c => [c[0], c[1]]),
                selected: data.settings.computer,
              }),
            ),
          ]),
          h('div.form-split', [
            h(
              'div.form-group.form-half',
              select({
                key: 'chat',
                name: i18n('chat'),
                choices: userSelectionChoices,
                selected: data.settings.chat,
              }),
            ),
            h(
              'div.form-group.form-half',
              select({
                key: 'sticky',
                name: i18n('study:enableSync'),
                choices: [
                  ['true', i18n('study:yesKeepEveryoneOnTheSamePosition')],
                  ['false', i18n('study:noLetPeopleBrowseFreely')],
                ],
                selected: `${data.settings.sticky}`,
              }),
            ),
          ]),
          h(
            'div.form-split',
            h('div.form-group.form-full', [
              h('label.form-label', { attrs: { for: 'study-description' } }, i18n('description')),
              h('textarea.form-control', {
                attrs: {
                  id: 'study-description',
                  value: data.description || '',
                  rows: 4,
                },
                hook: {
                  insert(vnode: VNode) {
                    const el = vnode.elm as HTMLInputElement;
                    el.value = data.description || '';
                  },
                },
              }),
            ]),
          ),
          formButton(isNew ? i18n('study:start') : i18n('save')),
        ],
      ),
      h('div.destructive', [
        isNew
          ? null
          : h(
              'form',
              {
                attrs: {
                  action: `/study/${data.id}/clear-chat`,
                  method: 'post',
                },
                hook: bindNonPassive('submit', _ =>
                  confirm(i18n('study:deleteTheStudyChatHistory')),
                ),
              },
              [h(emptyRedButton, i18n('study:clearChat'))],
            ),
        h(
          'form',
          {
            attrs: {
              action: `/study/${data.id}/delete`,
              method: 'post',
            },
            hook: bindNonPassive(
              'submit',
              _ => isNew || confirm(i18n('study:deleteTheEntireStudy')),
            ),
          },
          [h(emptyRedButton, isNew ? i18n('cancel') : i18n('study:deleteStudy'))],
        ),
      ]),
    ],
  });
}
