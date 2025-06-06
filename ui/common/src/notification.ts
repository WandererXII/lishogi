import { assetUrl } from './assets';

let notifications: Array<Notification> = [];
let listening = false;

function listenToFocus() {
  if (!listening) {
    listening = true;
    window.addEventListener('focus', () => {
      notifications.forEach(n => n.close());
      notifications = [];
    });
  }
}

function notify(msg: string | (() => string)) {
  const storage = window.lishogi.storage.make('just-notified');
  if (document.hasFocus() || Date.now() - Number.parseInt(storage.get()!, 10) < 1000) return;
  storage.set(`${Date.now()}`);
  if (typeof msg === 'function') msg = msg();
  const notification = new Notification('lishogi.org', {
    icon: assetUrl('logo/lishogi-favicon-256.png', {
      noVersion: true,
    }),
    body: msg,
  });
  notification.onclick = () => window.focus();
  notifications.push(notification);
  listenToFocus();
}

export default function (msg: string | (() => string)): void {
  if (document.hasFocus() || !('Notification' in window)) return;
  if (Notification.permission === 'granted') {
    // increase chances that the first tab can put a local storage lock
    setTimeout(notify, 10 + Math.random() * 500, msg);
  }
}
