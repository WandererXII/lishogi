import { loadCssPath, loadLishogiScript } from 'common/assets';
import { wsSend } from 'common/ws';
import { initiatingHtml } from './util';

export function notifyApp(): typeof window.lishogi.notifyApp {
  let instance: any;
  let booted: boolean;
  const $toggle = $('#notify-toggle');
  const isVisible = () => $('#notify-app').is(':visible');
  const permissionChanged = () => {
    if (instance) instance.redraw();
  };

  if ('permissions' in navigator)
    navigator.permissions.query({ name: 'notifications' }).then(perm => {
      perm.onchange = permissionChanged;
    });

  permissionChanged();

  const load = (data?: any, incoming?: any) => {
    if (booted) return;
    booted = true;
    $('#notify-app').html(initiatingHtml);
    loadCssPath('notify');
    loadLishogiScript('notify').then(() => {
      instance = window.lishogi.modules.notify!({
        data: data,
        incoming: incoming,
        isVisible: isVisible,
        setCount(nb: number) {
          $toggle.find('span').attr('data-count', nb);
        },
        setNotified() {
          wsSend('notified');
        },
        pulse() {
          $toggle.addClass('pulse');
        },
      });
    });
  };

  $toggle
    .one('mouseover click', () => load())
    .on('click', () => {
      if ('Notification' in window) Notification.requestPermission(() => permissionChanged());
      setTimeout(() => {
        if (instance && isVisible()) instance.setVisible();
      }, 200);
    });

  return {
    update(data: any, incoming: boolean) {
      if (!instance) load(data, incoming);
      else instance.update(data, incoming);
    },
    setMsgRead(user: string) {
      if (!instance) load();
      else instance.setMsgRead(user);
    },
  };
}
