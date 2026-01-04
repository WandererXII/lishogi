import { wsConnect, wsSend } from 'common/ws';

function main(opts: any): void {
  let accepting: boolean;
  const selector = '.challenge-page';

  wsConnect(`/challenge/${opts.data.challenge.id}/socket/v5`, opts.data.socketVersion, {
    options: { reloadOnResume: true },
    events: {
      reload: () => {
        if (!accepting)
          window.lishogi.xhr
            .text('GET', opts.xhrUrl)
            .then(html => {
              $(selector).replaceWith($(html).find(selector));
              init();
              window.lishogi.pubsub.emit('content_loaded');
            })
            .catch(() => {
              setTimeout(window.lishogi.reload, 1500);
            });
        accepting = false;
      },
    },
  });

  function init() {
    $('#challenge-redirect').each(function (this: HTMLAnchorElement) {
      location.href = this.href;
    });
    $(selector)
      .find('form.accept')
      .on('submit', function () {
        accepting = true;
        $(this).html('<span class="ddloader"></span>');
      });
    $(selector)
      .find('form.xhr')
      .on('submit', function (this: HTMLFormElement, e) {
        e.preventDefault();
        window.lishogi.xhr.formToXhr(this);
        $(this).html('<span class="ddloader"></span>');
      });
    $(selector)
      .find('input.friend-autocomplete')
      .each(function () {
        const $input = $(this);
        window.lishogi.userAutocomplete($input, {
          focus: 1,
          friend: 1,
          tag: 'span',
          onSelect: () => {
            $input.parents('form').trigger('submit');
          },
        });
      });
  }

  init();

  function pingNow() {
    if (document.getElementById('ping-challenge')) {
      wsSend('ping');
      setTimeout(pingNow, 9000);
    }
  }

  pingNow();
}

window.lishogi.registerModule(__bundlename__, main);
