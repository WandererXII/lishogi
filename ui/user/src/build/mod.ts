import { spinnerHtml } from 'common/spinner';
import { debounce } from 'common/timings';
import { setupTranslator } from 'i18n/translator';
// @ts-ignore
import tablesort from 'tablesort';

const $toggle = $('.mod-zone-toggle');
const $zone = $('.mod-zone');
let nbOthers = 100;

function streamLoad() {
  const source = new EventSource(`${$toggle.attr('href')}?nbOthers=${nbOthers}`);
  const callback = debounce(() => userMod($zone), 300);
  source.addEventListener('message', e => {
    if (!e.data) return;
    const parser = new DOMParser();
    const doc = parser.parseFromString(e.data, 'text/html');
    const html = $('<output>').append($(doc.body).children());

    html.find('.mz-section').each(function () {
      const prev = $(`#${this.id}`);
      if (prev.length) prev.replaceWith($(this));
      else $zone.append($(this).clone());
    });
    callback();
  });
  source.onerror = () => source.close();
}

function loadZone() {
  $zone.html(spinnerHtml).removeClass('none');
  $('#main-wrap').addClass('full-screen-force');
  $zone.html('');
  streamLoad();
  window.addEventListener('scroll', onScroll);
  scrollTo('.mod-zone');
}
function unloadZone() {
  $zone.addClass('none');
  $('#main-wrap').removeClass('full-screen-force');
  window.removeEventListener('scroll', onScroll);
  scrollTo('#top');
}
function reloadZone() {
  streamLoad();
}

function scrollTo(el: string) {
  const offset = $('#inquiry').length ? -50 : 50;
  window.scrollTo(0, document.querySelector<HTMLElement>(el)!.offsetTop + offset);
}

$toggle.on('click', () => {
  if ($zone.hasClass('none')) loadZone();
  else unloadZone();
  return false;
});

function userMod($zone: any): void {
  const getLocationHash = (a: HTMLAnchorElement) => a.href.replace(/.+(#\w+)$/, '$1');

  window.lishogi.pubsub.emit('content_loaded');

  $('#mz_menu > a:not(.available)').each(function (this: HTMLAnchorElement) {
    $(this).toggleClass('available', !!$(getLocationHash(this)).length);
  });
  makeReady('#mz_menu', el => {
    $(el)
      .find('a')
      .each(function (i) {
        const id = getLocationHash(this as HTMLAnchorElement);
        const n = `${i + 1}`;
        $(this).prepend(`<i>${n}</i>`);
        window.lishogi.mousetrap.bind(n, () => scrollTo(id));
      });
  });

  makeReady('form.xhr', (el: HTMLFormElement) => {
    $(el).on('submit', () => {
      $(el).addClass('ready').find('input').prop('disabled', true);
      window.lishogi.xhr
        .formToXhr(el)
        .then(res => res.text())
        .then(html => {
          $zone.find('#mz_actions').replaceWith(html);
          userMod($zone);
        });
      return false;
    });
  });

  makeReady('form.fide_title select', el => {
    $(el).on('change', () => {
      $(el).parent('form').trigger('submit');
    });
  });

  makeReady('#mz_others', el => {
    $(el).height($(el).height()!);
    $(el)
      .find('.mark-alt')
      .on('click', function (this: HTMLAnchorElement) {
        if (confirm('Close alt account?')) {
          window.lishogi.xhr.text('POST', $(this).attr('href')!);
          $(this).remove();
        }
      });
  });
  makeReady('#mz_others table', el => {
    tablesort(el, { descending: true });
  });
  makeReady('#mz_identification .spy_filter', el => {
    $(el)
      .find('.button')
      .on('click', function (this: HTMLAnchorElement) {
        window.lishogi.xhr.text('POST', $(this).attr('href')!);
        $(this).parent().parent().toggleClass('blocked');
        return false;
      });
    $(el)
      .find('tr')
      .on('mouseenter', function () {
        const v = $(this).find('td:first').text();
        $('#mz_others tbody tr').each(function () {
          $(this).toggleClass('none', !($(this).data('tags') || '').includes(v));
        });
      });
    $(el).on('mouseleave', () => {
      $('#mz_others tbody tr').removeClass('none');
    });
  });
  makeReady(
    '#mz_identification .slist--sort',
    el => {
      tablesort(el, { descending: true });
    },
    'ready-sort',
  );
  makeReady('#mz_others .more-others', el => {
    $(el)
      .addClass('.ready')
      .on('click', () => {
        nbOthers = 1000;
        reloadZone();
      });
  });
}

function makeReady(selector: string, f: (el: Element, i: number) => any, cls?: string): void {
  cls = cls || 'ready';
  $zone.find(`${selector}:not(.${cls})`).each(function (i) {
    f($(this).addClass(cls)[0], i);
  });
}

const onScroll = () =>
  requestAnimationFrame(() => {
    if ($zone.hasClass('none')) return;
    $zone.toggleClass('stick-menu', window.scrollY > 200);
  });

(() => {
  const cleanNumber = (i: string) => i.replace(/[^\-?0-9.]/g, '');
  const compareNumber = (a: any, b: any) => {
    a = Number.parseFloat(a);
    b = Number.parseFloat(b);

    a = isNaN(a) ? 0 : a;
    b = isNaN(b) ? 0 : b;

    return a - b;
  };

  tablesort.extend(
    'number',
    (item: any) => {
      return item.match(/^[-+]?(\d)*-?([,.]){0,1}-?(\d)+([E,e][-+][\d]+)?%?$/); // Number
    },
    (a: any, b: any) => compareNumber(cleanNumber(b), cleanNumber(a)),
  );
})();

if (location.search.startsWith('?mod')) $toggle.trigger('click');
window.lishogi.mousetrap.bind('m', () => $toggle.trigger('click'));

setupTranslator('#inquiry div.docs.reports p, .player_chats .chat');
window.lishogi.pubsub.on('content_loaded', () => {
  setupTranslator('.mz_reports form .atom-text');
});
