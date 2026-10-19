import { flatpickr } from 'common/assets';
import { icons } from 'common/icons';
import { modal } from 'common/modal';
import type { MaybeVNode } from 'common/snabbdom';
import throttle from 'common/throttle';
import { ids } from 'game/status';
import { i18n } from 'i18n';
import { colorName } from 'shogi/color-name';
import { opposite } from 'shogiground/util';
import { h, type VNode } from 'snabbdom';
import type TournamentController from '../../ctrl';
import type { Arrangement, ArrangementUser } from '../../interfaces';
import xhr from '../../xhr';
import { flatpickrConfig, playerName, preloadUserTips, player as renderPlayer } from '../util';

let fInstance: any = null;
let showCalendar = false;

let interval: number | undefined;
const onlineCache = new Map<string, boolean>();

export function arrangementModal(ctrl: TournamentController): MaybeVNode {
  const a = ctrl.arrangement;
  if (!a) return;

  const throttleLoadOnlineStatus = throttle(15000, () => loadOnlineStatus(ctrl));
  const maybeLoadOnlineStatus = () => {
    if (document.visibilityState === 'visible') throttleLoadOnlineStatus();
  };

  return modal({
    class: 'arrangement__modal',
    onInsert(el) {
      preloadUserTips(el);

      document.addEventListener('visibilitychange', maybeLoadOnlineStatus);
      interval = setInterval(maybeLoadOnlineStatus, 35000);
      loadOnlineStatus(ctrl);
    },
    onClose() {
      document.removeEventListener('visibilitychange', maybeLoadOnlineStatus);
      clearInterval(interval);

      showCalendar = false;
      ctrl.showArrangement(undefined);

      ctrl.redraw();
    },
    content: [h('div.arr-modal', [header(ctrl, a), users(ctrl, a), totalSection(ctrl, a)])],
  });
}

function loadOnlineStatus(ctrl: TournamentController): void {
  const userId1 = ctrl.arrangement?.user1?.id;
  const userId2 = ctrl.arrangement?.user2?.id;
  const users = [userId1, userId2].filter(u => u);

  if (users.length)
    window.lishogi.xhr
      .json('GET', '/api/users/status', {
        url: {
          ids: `${users.join(',')}`,
        },
      })
      .then((res: { id: string; online?: boolean }[]) => {
        if (userId1) onlineCache.set(userId1, !!res.find(p => p.id === userId1)?.online);
        if (userId2) onlineCache.set(userId2, !!res.find(p => p.id === userId2)?.online);
        ctrl.redraw();
      })
      .catch(() => {
        if (userId1) onlineCache.delete(userId1);
        if (userId2) onlineCache.delete(userId2);
        ctrl.redraw();
      });
}

function header(ctrl: TournamentController, a: Arrangement): VNode {
  return h('div.arr-header', [
    ctrl.isCreator() && ctrl.isOrganized()
      ? h(
          'a.edit-icon',
          {
            attrs: {
              title: i18n('edit'),
            },
            on: {
              click: () => {
                ctrl.showArrangement(undefined);
                ctrl.showOrganizerArrangement(a);
              },
            },
          },
          h('i', {
            attrs: {
              'data-icon': icons.gear,
            },
          }),
        )
      : null,
    ctrl.isCreator() && !ctrl.isOrganized() && a.gameId && !ctrl.data.isFinished
      ? h(
          'a.annul-icon',
          {
            attrs: {
              title: i18n('tourArrangements:annulResults'),
            },
            on: {
              click: () => {
                if (
                  confirm(`${i18n('tourArrangements:annulResults')} - ${i18n('notReversible')}`)
                ) {
                  ctrl.annulGame(a.id, a.gameId);
                }
              },
            },
          },
          h('i', {
            attrs: {
              'data-icon': icons.reload,
            },
          }),
        )
      : null,
    h('h3', a.name || i18n('tourArrangements:gameScheduling')),
  ]);
}

function users(ctrl: TournamentController, a: Arrangement): VNode {
  const isFlipped = a.user1?.id === ctrl.opts.userId;
  const users = isFlipped ? [a.user2, a.user1] : [a.user1, a.user2];

  return h(
    'div.arr-users',
    users.map((u, i) => playerSection(ctrl, a, u, i === 0 ? 'top' : 'bottom')),
  );
}

function playerSection(
  ctrl: TournamentController,
  a: Arrangement,
  user: ArrangementUser | undefined,
  pos: 'top' | 'bottom',
): VNode {
  const player = ctrl.data.standing.players.find(p => p.id === user?.id);

  return h(
    `div.arr-user.arr-user-${pos}`,
    user && player
      ? [
          h('div.arr-name', [
            renderPlayer(player, {
              asLink: true,
              withRating: true,
              status: {
                online: !!onlineCache.get(player.id),
              },
            }),
            colors(a, user),
            points(ctrl, a, user),
          ]),
          action(ctrl, a, user),
        ]
      : h('div.arr-name', h('div.user-link', [h('i.line'), ...playerName(player)])),
  );
}

function action(ctrl: TournamentController, a: Arrangement, user: ArrangementUser): MaybeVNode {
  if (!ctrl.arrangementHasMe(a) || ctrl.data.isFinished || a.gameId || a.locked) return;
  else
    return user.id === ctrl.opts.userId
      ? myTimeInput(ctrl, a, user)
      : opponentTimeInput(ctrl, a, user);
}

function opponentTimeInput(
  ctrl: TournamentController,
  a: Arrangement,
  user: ArrangementUser,
): VNode {
  return h('div.suggested-time-wrap', [
    h('input.disabled', {
      key: user.scheduledAt || '',
      attrs: {
        title: i18n('tourArrangements:suggestedTime'),
        disabled: true,
        placeholder: i18n('tourArrangements:suggestedTime'),
      },
      hook: {
        insert: vnode => {
          const el = vnode.elm as HTMLInputElement;
          if (user.scheduledAt) el.value = new Date(user.scheduledAt).toLocaleString();
          else el.value = '';
        },
      },
    }),
    h('button.fbt', {
      attrs: {
        'data-icon': icons.correct,
        disabled: !user.scheduledAt,
        title: i18n('tourArrangements:acceptSuggestedTime'),
      },
      on: {
        click: () => {
          if (user.scheduledAt) ctrl.arrangementTime(a, new Date(user.scheduledAt));
        },
      },
    }),
  ]);
}

function myTimeInput(ctrl: TournamentController, a: Arrangement, user: ArrangementUser): VNode {
  return h(
    'div.suggested-time-wrap',
    {
      class: {
        'hide-calendar': !showCalendar,
      },
    },
    [
      h(
        'div.flatpickr-input-wrap',
        {
          on: {
            click: () => {
              if (!showCalendar) {
                showCalendar = true;
                ctrl.redraw();
                const el = document.querySelector('.arr-user-bottom')!;
                if (showCalendar) el.scrollIntoView({ behavior: 'smooth', block: 'start' });
              }
            },
          },
        },
        h('input.flatpickr', {
          key: user.scheduledAt,
          attrs: {
            title: user.scheduledAt
              ? i18n('tourArrangements:suggestDifferentTime')
              : i18n('tourArrangements:suggestedTime'),
            disabled: !!ctrl.data.isFinished || !!a.gameId,
            placeholder: i18n('tourArrangements:suggestTime'),
          },
          hook: {
            insert: (node: VNode) => {
              flatpickr().then(() => {
                fInstance = window.flatpickr(node.elm as HTMLInputElement, {
                  ...flatpickrConfig,
                  inline: true,
                  maxDate: ctrl.dateToFinish,
                  onChange: () => {
                    ctrl.redraw();
                  },
                });
                if (user.scheduledAt) {
                  const scheduledDate = new Date(user.scheduledAt);
                  fInstance.setDate(scheduledDate, false);
                  fInstance.altInput.value = scheduledDate.toLocaleString();
                }
              });
            },
            destroy: () => {
              if (fInstance) fInstance.destroy();
              fInstance = null;
            },
          },
        }),
      ),
      h('div.calendar-button-wrap', [
        h(
          'button.button.button-green.text',
          {
            attrs: {
              'data-icon': icons.correct,
            },
            on: {
              click: () => {
                showCalendar = false;
                ctrl.redraw();
                if (fInstance && fInstance.selectedDates[0]?.getTime() !== user.scheduledAt)
                  ctrl.arrangementTime(a, fInstance.selectedDates[0]);
              },
            },
          },
          i18n('confirm'),
        ),
      ]),
    ],
  );
}

function points(ctrl: TournamentController, a: Arrangement, user: ArrangementUser): MaybeVNode {
  if (a.status && a.status >= ids.mate) {
    const points = a.points || ctrl.defaultArrangementPoints;
    const isWinner = a.winner === user.id;
    const isLoser = a.winner && !isWinner;
    const userPoints = isWinner ? points.w : isLoser ? points.l : points.d;
    return h(`div.points${isWinner ? '.winner' : isLoser ? '.loser' : ''}`, userPoints);
  } else return;
}

function colors(a: Arrangement, user: ArrangementUser): MaybeVNode {
  if (!a.gameId && a.color) {
    const isColorPlayer = a.user1?.id === user.id;
    return h(`div.color-icon.${isColorPlayer ? a.color : opposite(a.color)}`, {
      attrs: {
        title: colorName(a.color, false),
      },
    });
  } else return;
}

function titleValueWrap(title: string, value: MaybeVNode, extra?: MaybeVNode): VNode {
  return h('div.title-value-wrap', [h('span.title', [title, ':']), h('span.value', value), extra]);
}

function totalSection(ctrl: TournamentController, a: Arrangement): VNode {
  const hasMe = ctrl.arrangementHasMe(a);
  const challenge = ctrl.challengeData.in?.find(c => {
    return c.tourInfo?.arrId === a.id;
  });

  const meReady = challenge?.challenger?.id === ctrl.opts.userId;
  const opponentReady = challenge?.destUser?.id === ctrl.opts.userId;
  const isReady = meReady || opponentReady;

  const points = ctrl.isOrganized() ? a.points || ctrl.defaultArrangementPoints : a.points;

  return h('div.total-section', [
    h('div.values', [
      !a.startedAt
        ? titleValueWrap(i18n('tourArrangements:scheduledAt'), formatDate(a.scheduledAt))
        : null,
      a.startedAt
        ? titleValueWrap(i18n('tourArrangements:startedAt'), formatDate(a.startedAt))
        : null,
      points
        ? titleValueWrap(i18n('tourArrangements:pointsWDL'), `${points.w}/${points.d}/${points.l}`)
        : null,
      a.prevGameIds
        ? titleValueWrap(
            i18n('tourArrangements:annulledGames'),
            h(
              'div.annulled-games',
              a.prevGameIds.map(gid =>
                h('a', { attrs: { href: `/${gid}`, target: '_blank' } }, `#${gid}`),
              ),
            ),
          )
        : null,
    ]),
    a.gameId
      ? h(
          'a.fbt.go-to-game',
          { attrs: { href: `/${a.gameId}` } },
          i18n('tourArrangements:goToGame'),
        )
      : !ctrl.data.isFinished && hasMe
        ? h('div.arr-start-wrap', [
            h(
              'button.fbt',
              {
                attrs: {
                  title: !ctrl.data.isStarted
                    ? `${i18n('starting')} ${new Date(ctrl.data.startsAt).toLocaleString()}`
                    : isReady
                      ? i18n('viewChallenge')
                      : i18n('challengeToPlay'),
                },
                class: {
                  ready: isReady,
                  disabled: !ctrl.data.isStarted || !!ctrl.myGameId() || !a.user1 || !a.user2,
                },
                on: {
                  click: () => {
                    xhr.challenge(ctrl, a);
                  },
                },
              },
              h('span', isReady ? i18n('viewChallenge') : i18n('challengeToPlay')),
            ),
            h('span.help', i18n('tourArrangements:gameWillNotStart')),
          ])
        : null,
  ]);
}

function formatDate(dateNumber: number | undefined): VNode {
  const date = dateNumber ? new Date(dateNumber) : undefined;
  return date
    ? h(
        'span.date',
        {
          attrs: {
            title: date.toUTCString(),
          },
        },
        `${date.toLocaleString()}`,
      )
    : h('span.date', '-');
}
