// @ts-nocheck

import { icons } from 'common/icons';
import { wsSend } from 'common/ws';
import { i18n, i18nVdomPlural } from 'i18n';

const widget = (name: string, prototype: any): any => {
  // biome-ignore lint/suspicious/noShadowRestrictedNames: Stupid widgets will be removed anyway
  const constructor = function (options, element) {
    this.element = $(element);
    $.data(element, name, this);
    this.options = options;
    this._create();
  };
  $[name] = constructor;
  constructor.prototype = prototype;
  $.fn[name] = function (method, ...args) {
    let returnValue = this;
    if (typeof method === 'string')
      this.each(function () {
        const instance = $.data(this, name);
        if (!instance) return;
        if (!$.isFunction(instance[method]) || method.charAt(0) === '_')
          return $.error(`no such method '${method}' for ${name} widget instance`);
        returnValue = instance[method].apply(instance, args);
      });
    else
      this.each(function () {
        if (!$.data(this, name)) $.data(this, name, new constructor(method, this));
      });
    return returnValue;
  };
};

interface WatchersData {
  users?: string[];
  nb?: number;
  anons?: number;
}

export function initWidgets(): void {
  const anonHtml = `<span class="anon">${i18n('anonymousUser')}</span>`;

  widget('watchers', {
    _create: function () {
      this.lines = {
        game: this.element.find('.line.game'),
        analysis: this.element.find('.line.analysis'),
        default: this.element.find('.line.default'),
      };

      window.lishogi.pubsub.on('socket.in.crowd', data => {
        if (data.game || data.analysis) {
          this.lines.default.addClass('none');
          this.renderLine(this.lines.game, data.game);
          this.renderLine(this.lines.analysis, data.analysis);
        } else {
          this.lines.game.addClass('none');
          this.lines.analysis.addClass('none');
          this.renderLine(this.lines.default, data.watchers || data);
        }

        const hasContent = this.element.find('.line:not(.none)').length > 0;
        this.element.toggleClass('none', !hasContent);
      });
    },

    renderLine: ($line: JQuery, data: WatchersData | undefined) => {
      if (!data || !data.nb) {
        $line.addClass('none');
        return;
      }

      const $number = $line.find('.number');
      const $list = $line.find('.list');

      if ($number.length) $number.text(data.nb);

      if (data.users || data.anons) {
        const tags = (data.users || []).map(u => {
          const username = u?.includes(' ') ? u.split(' ')[1] : u;
          return username
            ? `<a class="user-link ulpt" href="/@/${username.toLowerCase()}">${username}</a>`
            : anonHtml;
        });

        if (data.anons === 1) tags.push(anonHtml);
        else if (data.anons) {
          tags.push(`<span class="anon">${i18n('anonymousUser')} (${data.anons})</span>`);
        }
        $list.html(tags.join(', '));
      } else if (!$number.length) {
        $list.html(`<span data-icon="${icons.person}">${data.nb}</span>`);
      }

      $line.removeClass('none');
    },
  });

  widget(
    'friends',
    (() => {
      const getId = (titleName: string) => titleName.toLowerCase().replace(/^\w+\s/, '');
      const makeUser = (titleName: string) => {
        const split = titleName.split(' ');
        return {
          id: split[split.length - 1].toLowerCase(),
          name: split[split.length - 1],
          title: split.length > 1 ? split[0] : undefined,
          playing: false,
          patron: false,
        };
      };
      const renderUser = (user: any) => {
        const icon = `<i class="line${user.patron ? ' patron' : ''}"></i>`;
        const titleTag = user.title
          ? `<span class="title"${user.title === 'BOT' ? ' data-bot' : ''}>${user.title}</span>&nbsp;`
          : '';
        const url = `/@/${user.name}`;
        const tvButton = user.playing
          ? `<a data-icon="${icons.television}" class="tv ulpt" data-pt-pos="nw" href="${url}/tv" data-href="${url}"></a>`
          : '';
        return `<div><a class="user-link ulpt" data-pt-pos="nw" href="${url}">${icon}${titleTag}${user.name}</a>${tvButton}</div>`;
      };
      return {
        _create: function () {
          const el = this.element;

          this.$friendBoxTitle = el.find('.friend_box_title').on('click', () => {
            el.find('.content_wrap').toggleNone();
            if (!this.loaded) {
              this.loaded = true;
              wsSend('following_onlines');
            }
          });

          this.$nobody = el.find('.nobody');

          const data = {
            users: [],
            playing: [],
            patrons: [],
            ...el.data('preload'),
          };
          this.set(data);
        },
        repaint: function () {
          if (this.loaded)
            requestAnimationFrame(
              function () {
                const users = this.users;
                const ids = Object.keys(users).sort();
                this.$friendBoxTitle.html(
                  i18nVdomPlural(
                    'nbFriendsOnline',
                    ids.length,
                    this.loaded ? $('<strong>').text(ids.length) : '-',
                  ),
                );
                this.$nobody.toggleNone(!ids.length);
                this.element.find('.list').html(ids.map(id => renderUser(users[id])).join(''));
              }.bind(this),
            );
        },
        insert: function (titleName) {
          const id = getId(titleName);
          if (!this.users[id]) this.users[id] = makeUser(titleName);
          return this.users[id];
        },
        set: function (d) {
          this.users = {};
          let i: any;
          for (i in d.users) this.insert(d.users[i]);
          for (i in d.playing) this.insert(d.playing[i]).playing = true;
          for (i in d.patrons) this.insert(d.patrons[i]).patron = true;
          this.repaint();
        },
        enters: function (d) {
          const user = this.insert(d.d);
          user.playing = d.playing;
          user.patron = d.patron;
          this.repaint();
        },
        leaves: function (titleName) {
          delete this.users[getId(titleName)];
          this.repaint();
        },
        playing: function (titleName) {
          this.insert(titleName).playing = true;
          this.repaint();
        },
        stopped_playing: function (titleName) {
          this.insert(titleName).playing = false;
          this.repaint();
        },
      };
    })(),
  );

  widget('clock', {
    _create: function () {
      const target = this.options.time * 1000 + Date.now();
      const timeEl = this.element.find('.time')[0];
      const tick = () => {
        const remaining = target - Date.now();
        if (remaining <= 0) clearInterval(this.interval);
        timeEl.innerHTML = this._formatMs(remaining, target);
      };
      this.interval = setInterval(tick, 1000);
      tick();
    },

    _pad: x => (x < 10 ? '0' : '') + x,

    _formatMs: function (msTime, target) {
      const ms = Math.max(0, msTime + 500);

      const seconds: number = Math.floor(ms / 1000) % 60;
      const minutes: number = Math.floor(ms / (1000 * 60)) % 60;
      const hours: number = Math.floor(ms / (1000 * 60 * 60));

      if (hours > 72) {
        const date = new Date(target);
        return date.toLocaleString();
      } else if (hours > 0) {
        return `${hours}:${this._pad(minutes)}:${this._pad(seconds)}`;
      } else {
        return `${minutes}:${this._pad(seconds)}`;
      }
    },
  });
}
