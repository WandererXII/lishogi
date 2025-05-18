import notify from 'common/notification';
import type { Ctrl, NotifyData, NotifyOpts } from './interfaces';
import { asText } from './view';

const li = window.lishogi;

export default function ctrl(opts: NotifyOpts, redraw: Redraw): Ctrl {
  let data: NotifyData | undefined;
  let initiating = true;
  let scrolling = false;

  const readAllStorage = li.storage.make('notify-read-all');

  readAllStorage.listen(_ => {
    if (data) {
      data.unread = 0;
      opts.setCount(0);
      redraw();
    }
  });

  function update(d: NotifyData, incoming: boolean) {
    data = d;
    if (data.pager.currentPage === 1 && data.unread && opts.isVisible()) {
      opts.setNotified();
      data.unread = 0;
      readAllStorage.fire();
    }
    initiating = false;
    scrolling = false;
    opts.setCount(data.unread);
    if (incoming) notifyNew();
    redraw();
  }

  function notifyNew() {
    if (!data || data.pager.currentPage !== 1) return;
    const notif = data.pager.currentPageResults.find(n => !n.read);
    if (!notif) return;
    opts.pulse();
    if (!li.quietMode) li.sound.play('newPM');
    const text = asText(notif);
    const pushSubscribed =
      Number.parseInt(li.storage.get('push-subscribed2') || '0', 10) + 86400000 >= Date.now(); // 24h
    if (!pushSubscribed && text) notify(text);
  }

  function loadPage(page: number) {
    return window.lishogi.xhr
      .json('GET', '/notify', { url: { page: page || 1 } })
      .then((d: any) => update(d, false))
      .catch(() => window.lishogi.announce({ msg: 'Failed to load notifications' }));
  }

  function nextPage() {
    if (!data || !data.pager.nextPage) return;
    scrolling = true;
    loadPage(data.pager.nextPage);
    redraw();
  }

  function previousPage() {
    if (!data || !data.pager.previousPage) return;
    scrolling = true;
    loadPage(data.pager.previousPage);
    redraw();
  }

  function setVisible() {
    if (!data || data.pager.currentPage === 1) loadPage(1);
  }

  function setMsgRead(user: string) {
    if (data)
      data.pager.currentPageResults.forEach(n => {
        if (n.type == 'privateMessage' && n.content.user.id == user && !n.read) {
          n.read = true;
          data!.unread = Math.max(0, data!.unread - 1);
          opts.setCount(data!.unread);
        }
      });
  }

  const emptyNotifyData = {
    pager: {
      currentPage: 1,
      maxPerPage: 1,
      currentPageResults: [],
      nbResults: 0,
      nbPages: 1,
    },
    unread: 0,
  };

  function clear() {
    window.lishogi.xhr.text('POST', '/notify/clear').then(
      _ => update(emptyNotifyData, false),
      _ => window.lishogi.announce({ msg: 'Failed to clear notifications' }),
    );
  }

  return {
    redraw,
    data: () => data,
    initiating: () => initiating,
    scrolling: () => scrolling,
    update,
    nextPage,
    previousPage,
    loadPage,
    setVisible,
    setMsgRead,
    clear,
  };
}
