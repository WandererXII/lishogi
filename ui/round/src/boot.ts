import { makeChat } from 'chat';
import type { ChatCtrl, ChatOpts } from 'chat/interfaces';
import { wsConnect } from 'common/ws';
import type { TourPlayer } from 'game/interfaces';
import { finished } from 'game/status';
import { i18n } from 'i18n';
import type RoundController from './ctrl';
import type { RoundData, RoundOpts } from './interfaces';
import { type TourStandingCtrl, tourStandingCtrl } from './tour-standing';

export function boot(
  opts: RoundOpts,
  start: (element: HTMLElement, opts: RoundOpts) => RoundController,
): RoundController {
  const li = window.lishogi;
  const element = document.querySelector('.round__app') as HTMLElement;
  const data: RoundData = opts.data;

  let ctrl: RoundController | undefined;
  let chat: ChatCtrl | undefined;
  if (data.tournament) $('body').data('tournament-id', data.tournament.id);

  // not ideal - flashes blue/grey board
  const bodyCls = document.body.classList;
  if ((data.game.isProMode && bodyCls.contains('blue')) || bodyCls.contains('gray')) {
    bodyCls.remove('blue', 'grey');
    bodyCls.add('wood');
  }

  function startTournamentClock() {
    if (data.tournament)
      $('.game__tournament-clock .clock').each(function (this: HTMLElement) {
        $(this).clock({
          time: Number.parseFloat($(this).data('time')),
        });
      });
  }
  function getPresetGroup(d: RoundData) {
    if (d.player.spectator) return;
    if (d.steps.length < 4) return 'start';
    else if (finished(d)) return 'end';
    return;
  }

  const socketUrl = opts.data.player.spectator
    ? `/watch/${data.game.id}/${data.player.color}/v6`
    : `/play/${data.game.id}${data.player.id}/v6`;

  if (data.player.version !== opts.socketVersion) {
    console.error('Socket version mismatch:', data.player.version, opts.socketVersion);
    setTimeout(() => {
      window.lishogi.xhr.text('POST', '/jsmon/playerVersion', {
        url: { v: `${window.location.pathname}:${opts.socketVersion}:${data.player.version}` },
      });
    }, 3000);
  }

  opts.socketSend = wsConnect(socketUrl, opts.socketVersion, {
    options: { reloadOnResume: true },
    params: { userTv: data.userTv?.id },
    receive(t: string, d: any) {
      ctrl?.socket.receive(t, d);
    },
    events: {
      tvSelect(o: any) {
        if (data.tv && data.tv.channel == o.channel) li.reload();
        else
          $(`.tv-channels .${o.channel} .champion`).html(
            o.player
              ? [o.player.title, o.player.name, o.player.rating].filter(x => x).join('&nbsp')
              : i18n('anonymousUser'),
          );
      },
      endData() {
        window.lishogi.xhr
          .text('GET', `${data.tv ? '/tv' : ''}/${data.game.id}/${data.player.color}/sides`)
          .then(html => {
            const $html = $(html);
            const $meta = $html.find('.game__meta');
            $meta.length && $('.game__meta').replaceWith($meta);
            $('.crosstable').replaceWith($html.find('.crosstable'));
            startTournamentClock();
            li.pubsub.emit('content_loaded');
          });
      },
      tourStanding(s: TourPlayer[]) {
        if (opts.chat?.plugin && chat) {
          (opts.chat.plugin as TourStandingCtrl).set(s);
          chat.redraw();
        }
      },
    },
  }).send;
  opts.klasses = Array.from(element.classList);

  if (!data.tournament && !data.simul)
    opts.onChange = (d: RoundData) => {
      if (chat) chat.preset.setGroup(getPresetGroup(d));
    };

  ctrl = start(element, opts);
  const chatOpts = opts.chat;
  if (chatOpts) {
    if (data.tournament?.top) {
      chatOpts.plugin = tourStandingCtrl(data.tournament.top, data.tournament.team);
      chatOpts.alwaysEnabled = true;
    } else if (!data.simul) {
      chatOpts.preset = getPresetGroup(data);
      chatOpts.parseMoves = true;
    }
    if (chatOpts.noteId && (chatOpts.noteAge || 0) < 10) chatOpts.noteText = '';
    chat = makeChat(chatOpts as ChatOpts);
  }
  startTournamentClock();
  $('.round__now-playing .move-on input')
    .on('change', ctrl.moveOn.toggle)
    .prop('checked', ctrl.moveOn.get())
    .on('click', 'a', () => {
      window.lishogi.properReload = true;
      return true;
    });
  if (location.pathname.lastIndexOf('/round-next/', 0) === 0)
    history.replaceState(null, '', `/${data.game.id}`);
  if (location.pathname.length === 9 && data.player.id && data.player.user)
    history.replaceState(null, '', `/${data.game.id}${data.player.id}`);
  $('#zentog').on('click', () => li.pubsub.emit('zen'));
  li.storage.make('reload-round-tabs').listen(li.reload);

  return ctrl;
}
