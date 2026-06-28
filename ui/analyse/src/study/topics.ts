import { loadCssPath, loadVendorScript } from 'common/assets';
import { type Prop, prop } from 'common/common';
import { icons } from 'common/icons';
import { modal } from 'common/modal';
import { bind, bindSubmit, dataIcon, onInsert } from 'common/snabbdom';
import { i18n } from 'i18n';
import { h, type VNode } from 'snabbdom';
import type { StudyCtrl, Topic } from './interfaces';

export interface TopicsCtrl {
  open: Prop<boolean>;
  getTopics(): Topic[];
  save(data: string): void;
  redraw: Redraw;
}

export function ctrl(
  save: (data: string) => void,
  getTopics: () => Topic[],
  redraw: Redraw,
): TopicsCtrl {
  const open = prop(false);

  return {
    open,
    getTopics,
    save(data: string) {
      save(data);
      open(false);
    },
    redraw,
  };
}

export function view(ctrl: StudyCtrl): VNode {
  return h('div.study__topics.chip-list', [
    ...ctrl.topics.getTopics().map(topic =>
      h(
        'a.chip',
        {
          attrs: { href: `/study/topic/${encodeURIComponent(topic)}/hot` },
        },
        topic,
      ),
    ),
    ctrl.members.canContribute()
      ? h(
          'a.chip.text',
          {
            attrs: dataIcon(icons.gear),
            hook: bind('click', () => ctrl.topics.open(true), ctrl.redraw),
          },
          i18n('study:manageTopics'),
        )
      : null,
  ]);
}

let tagify: any | undefined;

export function formView(ctrl: TopicsCtrl, userId?: string): VNode {
  return modal({
    class: 'study__modal.study-topics',
    onClose() {
      ctrl.open(false);
      ctrl.redraw();
    },
    content: [
      h('h2', i18n('study:studyTopics')),
      h(
        'form',
        {
          hook: bindSubmit(_ => {
            const tags = tagify?.value;
            tags && ctrl.save(tags.map((t: any) => t.value));
          }, ctrl.redraw),
        },
        [
          h(
            'textarea',
            {
              hook: onInsert(elm => setupTagify(elm as HTMLTextAreaElement, userId)),
            },
            ctrl.getTopics().join(', ').replace(/[<>]/g, ''),
          ),
          h(
            'button.button',
            {
              type: 'submit',
            },
            i18n('apply'),
          ),
        ],
      ),
    ],
  });
}

function setupTagify(elm: HTMLTextAreaElement, userId?: string) {
  loadCssPath('misc.tagify');
  loadVendorScript('tagify', 'tagify.js').then(() => {
    tagify = new window.Tagify(elm, { pattern: /.{2,}/, maxTags: 30 });
    const tagi = tagify;
    let abortCtrl: AbortController | undefined; // for aborting the call
    tagi.on('input', (e: any) => {
      const term = (e.detail as Tagify.TagData).value.trim();
      if (term.length < 2) return;
      tagi.settings.whitelist.length = 0; // reset the whitelist
      abortCtrl?.abort();
      abortCtrl = new AbortController();
      // show loading animation and hide the suggestions dropdown
      tagi.loading(true).dropdown.hide.call(tagi);
      window.lishogi.xhr
        .json(
          'GET',
          '/study/topic/autocomplete',
          { url: { term, user: userId } },
          {
            signal: abortCtrl.signal,
          },
        )
        .then(list => {
          tagi.settings.whitelist.splice(0, list.length, ...list); // update whitelist Array in-place
          tagi.loading(false).dropdown.show.call(tagi, term); // render the suggestions dropdown
        });
    });
    $('.tagify__input').each(function (this: HTMLInputElement) {
      this.focus();
    });
  });
}
