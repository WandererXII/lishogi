import { escapeHtml } from 'common/string';
import { pubsub } from './pubsub';

let timeout: Timeout | undefined;
const kill = () => {
  if (timeout) clearTimeout(timeout);
  timeout = undefined;
  document.querySelectorAll('.flash').forEach(el => {
    el.remove();
  });
};
export const announce = (d: LishogiAnnouncement): void => {
  if (!d) return;
  kill();
  if (d.msg) {
    document.body.insertAdjacentHTML(
      'beforeend',
      `<div class="flash flash-${d.tpe || 'failure'}">
        <div class="flash__content">
          ${escapeHtml(d.msg)}
        </div>
      </div>`,
    );
    timeout = setTimeout(kill, 6000);
    pubsub.emit('content_loaded');
  }
};
