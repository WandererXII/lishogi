import { numberFormat } from 'common/number';
import type LobbyController from './ctrl';
import type { LobbyOpts } from './interfaces';
import type { SetupKey } from './setup/ctrl';

export default function boot(
  opts: LobbyOpts,
  start: (opts: LobbyOpts) => LobbyController,
): LobbyController {
  let ctrl: LobbyController | undefined = undefined;
  const nbRoundSpread = spreadNumber('#nb_games_in_play > strong', 8);
  const nbUserSpread = spreadNumber('#nb_connected_players > strong', 10);
  const getParameterByName = <T extends string>(name: string): T | undefined => {
    const match = RegExp(`[?&]${name}=([^&]*)`).exec(location.search);
    return match ? (decodeURIComponent(match[1].replace(/\+/g, ' ')) as T) : undefined;
  };

  window.lishogi.socket = new window.lishogi.StrongSocket('/lobby/socket/v4', false, {
    receive: (t: string, d: any) => {
      ctrl?.socket.receive(t, d);
    },
    events: {
      n: (_nbUsers: number, msg: any) => {
        nbUserSpread(msg.d);
        setTimeout(() => {
          nbRoundSpread(msg.r);
        }, window.lishogi.socket.pingInterval() / 2);
      },
      reload_timeline: () => {
        window.lishogi.xhr.text('GET', '/timeline').then(html => {
          $('.timeline').html(html);
          window.lishogi.pubsub.emit('content_loaded');
        });
      },
      featured: (o: any) => {
        $('.lobby__tv').html(o.html);
        window.lishogi.pubsub.emit('content_loaded');
      },
      redirect: (e: any) => {
        ctrl?.setRedirecting();
        window.lishogi.redirect(e);
      },
      tournaments: (data: string) => {
        $('#enterable_tournaments').html(data);
        window.lishogi.pubsub.emit('content_loaded');
      },
      sfen: (e: any) => {
        window.lishogi.StrongSocket.defaultParams.events.sfen(e);
        ctrl?.gameActivity(e.id);
      },
    },
    options: {
      name: 'lobby',
    },
  });

  opts.blindMode = $('body').hasClass('blind-mode');
  opts.socketSend = window.lishogi.socket.send;
  opts.variant = getParameterByName<VariantKey>('variant');
  opts.sfen = getParameterByName<Sfen>('sfen');
  opts.hookLike = getParameterByName('hook_like');

  ctrl = start(opts);

  const $startButtons = $('.lobby__start');
  const clickEvent = opts.blindMode ? 'click' : 'mousedown';

  $startButtons
    .find('button:not(.disabled)')
    .on(clickEvent, function (this: HTMLElement) {
      $(this).addClass('active').siblings().removeClass('active');
      const cls = this.classList;
      const sKey = cls.contains('config_hook')
        ? 'hook'
        : cls.contains('config_friend')
          ? 'friend'
          : 'ai';
      ctrl.setupCtrl.open(sKey);
      ctrl.redraw();
      return false;
    })
    .on('click', () => false);

  const hash = location.hash;

  if (['#ai', '#friend', '#hook'].includes(hash)) {
    const setupData: Record<string, string> = location.search
      ? location.search
          .slice(1)
          .split('&')
          .map(p => p.split('='))
          .reduce((obj, [key, value]) => ({ ...obj, [key]: value }), {})
      : {};

    if (setupData.sfen) {
      setupData.position = '1';
      setupData.handicap = '';
    }

    ctrl.setupCtrl.open(location.hash.slice(1) as SetupKey, setupData);

    if (location.hash === '#hook') {
      if (/time=realTime/.test(location.search)) ctrl.setTab('real_time');
      else if (/time=correspondence/.test(location.search)) ctrl.setTab('seeks');
      ctrl.redraw();
    }

    history.replaceState(null, '', '/');
  }

  return ctrl;
}

function spreadNumber(selector: string, nbSteps: number) {
  const el = document.querySelector(selector) as HTMLElement;
  let previous = Number.parseInt(el.getAttribute('data-count')!);
  const display = (prev: number, cur: number, it: number) => {
    el.textContent = numberFormat(
      Math.round((prev * (nbSteps - 1 - it) + cur * (it + 1)) / nbSteps),
    );
  };
  let timeouts: number[] = [];
  return (nb: number, overrideNbSteps?: number) => {
    if (!el || (!nb && nb !== 0)) return;
    if (overrideNbSteps) nbSteps = Math.abs(overrideNbSteps);
    timeouts.forEach(clearTimeout);
    timeouts = [];
    const interv = Math.abs(window.lishogi.socket.pingInterval() / nbSteps);
    const prev = previous || nb;
    previous = nb;
    for (let i = 0; i < nbSteps; i++)
      timeouts.push(setTimeout(display.bind(null, prev, nb, i), Math.round(i * interv)));
  };
}
