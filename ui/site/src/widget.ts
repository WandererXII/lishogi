// @ts-nocheck

import { icons } from 'common/icons';
import { i18n } from 'i18n';

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
