import spinner from 'common/spinner';
import { i18n } from 'i18n';
import { type VNode, h } from 'snabbdom';
import type { Ctrl, NotifyData } from './interfaces';
import type { Notification } from './interfaces';
import { renderers } from './renderers';

export default function (ctrl: Ctrl): VNode {
  const d = ctrl.data();

  return h(
    'div#notify-app.links.dropdown',
    d && !ctrl.initiating() ? renderContent(ctrl, d) : [h('div.initiating', spinner())],
  );
}

function renderContent(ctrl: Ctrl, d: NotifyData): VNode[] {
  const pager = d.pager;
  const nb = pager.currentPageResults.length;

  const nodes: VNode[] = [];

  if (pager.previousPage)
    nodes.push(
      h('div.pager.prev', {
        attrs: { 'data-icon': 'S' },
        hook: clickHook(ctrl.previousPage),
      }),
    );
  else if (nb > 0)
    nodes.push(
      h(
        'div.clear',
        h('button.delete.button.button-empty', {
          attrs: {
            'data-icon': 'q',
            title: i18n('delete'),
          },
          hook: clickHook(ctrl.clear),
        }),
      ),
    );

  nodes.push(nb ? recentNotifications(d, ctrl.scrolling()) : empty());

  if (pager.nextPage)
    nodes.push(
      h('div.pager.next', {
        attrs: { 'data-icon': 'R' },
        hook: clickHook(ctrl.nextPage),
      }),
    );

  if (!('Notification' in window))
    nodes.push(h('div.browser-notification', 'Browser does not support notification popups'));
  else if (Notification.permission == 'denied') nodes.push(notificationDenied());

  return nodes;
}

export function asText(n: Notification): string | undefined {
  return renderers[n.type] ? renderers[n.type].text(n) : undefined;
}

function notificationDenied(): VNode {
  return h(
    'a.browser-notification.denied',
    {
      attrs: {
        href: '/faq#browser-notifications',
        target: '_blank',
      },
    },
    'Notification popups disabled by browser setting',
  );
}

function asHtml(n: Notification): VNode | undefined {
  return renderers[n.type] ? renderers[n.type].html(n) : undefined;
}

function clickHook(f: () => void) {
  return {
    insert: (vnode: VNode) => {
      (vnode.elm as HTMLElement).addEventListener('click', f);
    },
  };
}

const contentLoaded = () => window.lishogi.pubsub.emit('content_loaded');

function recentNotifications(d: NotifyData, scrolling: boolean): VNode {
  return h(
    'div',
    {
      class: {
        notifications: true,
        scrolling,
      },
      hook: {
        insert: contentLoaded,
        postpatch: contentLoaded,
      },
    },
    d.pager.currentPageResults.map(asHtml) as VNode[],
  );
}

function empty() {
  return h('div.empty.text', { attrs: { 'data-icon': '' } }, 'No notifications.');
}
