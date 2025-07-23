import { loadCssPath } from 'common/assets';
import { prop, requestIdleCallbackWithFallback } from 'common/common';
import type {
  ChatCtrl,
  ChatOpts,
  Line,
  ModerationCtrl,
  Permissions,
  Tab,
  ViewModel,
} from './interfaces';
import { moderationCtrl } from './moderation';
import { noteCtrl } from './note';
import { presetCtrl } from './preset';

const li = window.lishogi;

export default function (opts: ChatOpts, redraw: Redraw): ChatCtrl {
  const data = opts.data;
  data.domVersion = 1; // increment to force redraw
  const maxLines = 200;
  const maxLinesDrop = 50; // how many lines to drop at once
  data.maxLineLength = 140;

  const palantir = {
    instance: undefined,
    loaded: false,
    enabled: prop(!!data.palantir),
  };

  const allTabs: Tab[] = ['discussion'];
  if (opts.noteId) allTabs.push('note');
  if (opts.plugin) allTabs.push(opts.plugin.tab.key);

  const tabStorage = li.storage.make('chat.tab');
  const storedTab = tabStorage.get();

  let moderation: ModerationCtrl | undefined;

  const vm: ViewModel = {
    tab: allTabs.find(tab => tab === storedTab) || allTabs[0],
    enabled: opts.alwaysEnabled || !li.storage.get('nochat'),
    loading: false,
    timeout: opts.timeout,
    writeable: opts.writeable,
  };

  /* If discussion is disabled, and we have another chat tab,
   * then select that tab over discussion */
  if (allTabs.length > 1 && vm.tab === 'discussion' && li.storage.get('nochat'))
    vm.tab = allTabs[1];

  const post = (text: string): void => {
    text = text.trim();
    if (!text) return;
    if (text.length > data.maxLineLength) {
      alert(`Max length: ${data.maxLineLength} chars. ${text.length} chars used.`);
      return;
    }
    li.pubsub.emit('socket.send', 'talk', text);
  };

  const onTimeout = (userId: string) => {
    data.lines.forEach(l => {
      if (l.u && l.u.toLowerCase() == userId) l.d = true;
    });
    if (userId == data.userId) vm.timeout = true;
    data.domVersion++;
    redraw();
  };

  const onReinstate = (userId: string) => {
    if (userId == data.userId) {
      vm.timeout = false;
      redraw();
    }
  };

  const onClear = () => {
    data.lines = [];
    data.domVersion++;
    redraw();
  };

  const onMessage = (line: Line) => {
    data.lines.push(line);
    const nb = data.lines.length;
    if (nb > maxLines) {
      data.lines.splice(0, nb - maxLines + maxLinesDrop);
      data.domVersion++;
    }
    redraw();
  };

  const onWriteable = (v: boolean) => {
    vm.writeable = v;
    redraw();
  };

  const onPermissions = (obj: Permissions) => {
    let p: keyof Permissions;
    for (p in obj) opts.permissions[p] = obj[p];
    instanciateModeration();
    redraw();
  };

  function instanciateModeration() {
    if (opts.permissions.timeout || opts.permissions.local) {
      moderation = moderationCtrl({
        reasons: opts.timeoutReasons || [{ key: 'other', name: 'Inappropriate behavior' }],
        permissions: opts.permissions,
        redraw,
      });
      loadCssPath('chat.mod');
    }
  }
  instanciateModeration();

  const note = opts.noteId
    ? noteCtrl({
        id: opts.noteId,
        text: opts.noteText,
        redraw,
      })
    : undefined;

  const preset = presetCtrl({
    initialGroup: opts.preset,
    post,
    redraw,
  });

  const subs: [string, PubsubCallback][] = [
    ['socket.in.message', onMessage],
    ['socket.in.chat_timeout', onTimeout],
    ['socket.in.chat_reinstate', onReinstate],
    ['socket.in.chat_clear', onClear],
    ['chat.writeable', onWriteable],
    ['chat.permissions', onPermissions],
    ['palantir.toggle', palantir.enabled],
  ];
  subs.forEach(([eventName, callback]) => li.pubsub.on(eventName, callback));

  const destroy = () => {
    subs.forEach(([eventName, callback]) => li.pubsub.off(eventName, callback));
  };

  const emitEnabled = () => li.pubsub.emit('chat.enabled', vm.enabled);
  emitEnabled();

  return {
    data,
    opts,
    vm,
    allTabs,
    setTab(t: Tab) {
      vm.tab = t;
      tabStorage.set(t);
      // It's a lame way to do it. Give me a break.
      if (t === 'discussion')
        requestIdleCallbackWithFallback(() => $('.mchat__say').trigger('focus'));
      redraw();
    },
    moderation: () => moderation,
    note,
    preset,
    post,
    plugin: opts.plugin,
    setEnabled(v: boolean) {
      vm.enabled = v;
      emitEnabled();
      if (!v) li.storage.set('nochat', '1');
      else li.storage.remove('nochat');
      redraw();
    },
    redraw,
    palantir,
    destroy,
  };
}
