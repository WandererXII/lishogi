import { loadCssPath } from 'common/assets';
import { icons } from 'common/icons';
import { wsSend } from 'common/ws';
import { i18n } from 'i18n';
import { pubsub } from './pubsub';
import { initiatingHtml } from './util';

export interface UserData {
  id: string;
  name: string;
  patron?: boolean;
  playing?: boolean;
  isBot?: boolean;
}

export function initFollowingApp(): void {
  let users: Record<string, UserData> = {};
  let booted = false;

  const toggleEl = document.getElementById('following-toggle');
  const app = document.getElementById('following-app');
  if (!toggleEl || !app) return;

  const redraw = () => {
    console.log(users);
    const values = Object.values(users);
    app.innerHTML = `
      <div class="following-wrap">
        ${
          values.length
            ? values
                .map(user => {
                  const titleTag = user.isBot ? `<span class="bot-tag">BOT</span>&nbsp;` : '';
                  const url = `/@/${user.name}`;

                  return `
            <a class="following-user" data-pt-pos="s" href="${url}">
              <span class="user-link ulpt" data-href="${url}">
              <i class="line${user.patron ? ' patron' : ''}"></i>${titleTag} ${user.name}
              </span>
            </a>`;
                })
                .join('')
            : `<span class="no-online text" data-icon="${icons.infoCircle}">Nobody is online</span>`
        }
          <a class="more text is-after" data-icon="${icons.right}" href="/@/${document.body.dataset.user}/following">${i18n('allYouFollow')}</a>
      </div>
    `;
  };

  const add = (name: string, patron: boolean | undefined, isBot: boolean | undefined): UserData => {
    const id = name.toLowerCase();
    if (!users[id])
      users[id] = {
        id,
        name,
        patron,
        isBot,
      };

    return users[id];
  };
  const remove = (name: string): void => {
    delete users[name.toLowerCase()];
  };

  const setupListeners = () => {
    pubsub.on('socket.in.following_onlines', (_, d: any) => {
      console.log('dddd', d);

      users = {};
      const names: string[] = d.d || [];
      const patrons: string[] = d.patrons || [];
      const bots: string[] = d.bots || [];

      names.forEach(name => {
        add(name, patrons.includes(name), bots.includes(name));
      });

      redraw();
    });
    pubsub.on('socket.in.following_enters', (_: unknown, d: any) => {
      add(d.d, d.patron, d.bot);

      redraw();
    });
    pubsub.on('socket.in.following_leaves', name => {
      console.log('following_leaves', name);
      remove(name);

      redraw();
    });
  };

  const handleToggleClick = () => {
    if (booted) return;
    booted = true;
    app.innerHTML = initiatingHtml;
    setupListeners();
    loadCssPath('misc.following').then(() => {
      wsSend('following_onlines');
    });
  };

  toggleEl?.addEventListener('click', handleToggleClick, { once: true });
}
