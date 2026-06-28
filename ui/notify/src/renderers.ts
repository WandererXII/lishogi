import { icons } from 'common/icons';
import { i18n, i18nFormat } from 'i18n';
import { h, type VNode } from 'snabbdom';
import type { Notification, Renderers } from './interfaces';

// function generic(n: Notification, url: string | undefined, icon: string, content: VNode[]): VNode {
export const renderers: Renderers = {
  genericLink: {
    html: n =>
      generic(n, n.content.url, n.content.icon, [
        h('span', [h('strong', n.content.title), drawTime(n)]),
        h('span', n.content.text),
      ]),
    text: n => n.content.title || n.content.text,
  },
  mention: {
    html: n =>
      generic(n, `/forum/redirect/post/${n.content.postId}`, icons.talkAlt, [
        h('span', [h('strong', userFullName(n.content.mentionedBy)), drawTime(n)]),
        h('span', ` mentioned you in « ${n.content.topic} ».`),
      ]),
    text: n => `${userFullName(n.content.mentionedBy)} mentioned you in « ${n.content.topic} ».`,
  },
  invitedStudy: {
    html: n =>
      generic(n, `/study/${n.content.studyId}`, icons.study, [
        h('span', [h('strong', userFullName(n.content.invitedBy)), drawTime(n)]),
        h('span', ` invited you to « ${n.content.studyName} ».`),
      ]),
    text: n => `${userFullName(n.content.invitedBy)} invited you to « ${n.content.studyName} ».`,
  },
  privateMessage: {
    html: n =>
      generic(n, `/inbox/${n.content.user.name}`, icons.talk, [
        h('span', [h('strong', userFullName(n.content.user)), drawTime(n)]),
        h('span', n.content.text),
      ]),
    text: n => `${userFullName(n.content.sender)}: ${n.content.text}`,
  },
  teamJoined: {
    html: n =>
      generic(n, `/team/${n.content.id}`, icons.people, [
        h('span', [h('strong', n.content.name), drawTime(n)]),
        h('span', 'You are now part of the team.'),
      ]),
    text: n => `You have joined  « ${n.content.name}  ».`,
  },
  titledTourney: {
    html: n =>
      generic(n, `/tournament/${n.content.id}`, icons.trophy, [
        h('span', [h('strong', 'Lishogi Titled Arena'), drawTime(n)]),
        h('span', n.content.text),
      ]),
    text: _ => 'Lishogi Titled Arena',
  },
  tournamentReminder: {
    html: n => {
      return generic(n, `/tournament/${n.content.id}`, icons.trophy, [
        h('span', [h('strong', [i18n('tournament')]), drawTime(n)]),
        h('span', [i18n('starting'), ' ', new Date(n.content.date).toLocaleString()]),
      ]);
    },
    text: _ => 'Tournament you signed up for is coming up',
  },
  arrangementReminder: {
    html: n => {
      return generic(n, `/tournament/${arrangementPath(n)}`, icons.clock, [
        h('span', [h('strong', [i18n('tourArrangements:yourUpcomingGames')]), drawTime(n)]),
        h('span', [
          i18nFormat(
            'tourArrangements:upcomingGameWithX',
            (n.content.users as { name: string }[]).find(
              (u: any) => u.name.toLowerCase() !== document.body.dataset.user,
            )?.name,
          ),
        ]),
      ]);
    },
    text: _ => 'Arranged match is coming up',
  },
  arrangementConfirmation: {
    html: n => {
      return generic(n, `/tournament/${arrangementPath(n)}`, icons.clock, [
        h('span', [h('strong', [i18n('tourArrangements:gameScheduling')]), drawTime(n)]),
        h('span', i18nFormat('tourArrangements:xAcceptedTime', userFullName(n.content.user))),
      ]);
    },
    text: _ => 'Opponent confirmed the time you suggested',
  },
  reportedBanned: {
    html: n =>
      generic(n, undefined, icons.infoCircle, [
        h('span', [h('strong', i18n('reportedSomeoneBanned'))]),
        h('span', i18n('thankYou')),
      ]),
    text: _ => i18n('reportedSomeoneBanned'),
  },
  gameEnd: {
    html: n => {
      let title: string;
      switch (n.content.win) {
        case true:
          title = i18n('youWon');
          break;
        case false:
          title = i18n('youLost');
          break;
        default:
          title = i18n('gameWasDraw');
      }
      let result: string;
      switch (n.content.win) {
        case true:
          result = i18nFormat(
            'victoryVsYInZ',
            i18n('victory'),
            userFullName(n.content.opponent),
            `#${n.content.id}`,
          );
          break;
        case false:
          result = i18nFormat(
            'defeatVsYInZ',
            i18n('defeat'),
            userFullName(n.content.opponent),
            `#${n.content.id}`,
          );
          break;
        default:
          result = i18nFormat(
            'drawVsYInZ',
            i18n('draw'),
            userFullName(n.content.opponent),
            `#${n.content.id}`,
          );
      }
      return generic(n, `/${n.content.id}`, icons.correspondence, [
        h('span', [h('strong', title), drawTime(n)]),
        h('span', result),
      ]);
    },
    text: n => {
      let result: string;
      switch (n.content.win) {
        case true:
          result = i18nFormat(
            'victoryVsYInZ',
            i18n('victory'),
            userFullName(n.content.opponent),
            `#${n.content.id}`,
          );
          break;
        case false:
          result = i18nFormat(
            'defeatVsYInZ',
            i18n('defeat'),
            userFullName(n.content.opponent),
            `#${n.content.id}`,
          );
          break;
        default:
          result = i18nFormat(
            'drawVsYInZ',
            i18n('draw'),
            userFullName(n.content.opponent),
            `#${n.content.id}`,
          );
      }
      return result;
    },
  },
  pausedGame: {
    html: n =>
      generic(n, `/${n.content.id}`, icons.play, [
        h('span', [h('strong', 'Do not forget!'), drawTime(n)]),
        h('span', `Adjourned game with ${userFullName(n.content.opponent)}`),
      ]),
    text: n => `Adjourned game with ${userFullName(n.content.opponent)}`,
  },
  planStart: {
    html: n =>
      generic(n, '/patron', icons.patron, [
        h('span', [h('strong', i18n('thankYou')), drawTime(n)]),
        h('span', i18n('patron:justBecamePatron')),
      ]),
    text: _ => i18n('patron:justBecamePatron'),
  },
  planExpire: {
    html: n =>
      generic(n, '/patron', icons.patron, [
        h('span', [h('strong', i18n('patron:expired')), drawTime(n)]),
        h('span', i18n('patron:considerRenewing')),
      ]),
    text: _ => i18n('patron:expired'),
  },
  ratingRefund: {
    html: n =>
      generic(n, '/player/myself', icons.infoCircle, [
        h('span', [h('strong', 'You lost to a cheater'), drawTime(n)]),
        h('span', `Refund: ${n.content.points} ${n.content.perf} rating points.`),
      ]),
    text: n => `Refund: ${n.content.points} ${n.content.perf} rating points.`,
  },
  corresAlarm: {
    html: n =>
      generic(n, `/${n.content.id}`, icons.correspondence, [
        h('span', [h('strong', i18n('timeAlmostUp')), drawTime(n)]),
        h('span', i18nFormat('gameAgainstX', n.content.op)),
      ]),
    text: _ => i18n('timeAlmostUp'),
  },
  articlePublished: {
    html: n =>
      generic(n, `/article/${n.content.id}`, icons.article, [
        h('span', [h('strong', i18n('article:article'))]),
        h('span', i18n('article:articlePublished')),
      ]),
    text: _ => i18n('article:articlePublished'),
  },
};

function generic(n: Notification, url: string | undefined, icon: string, content: VNode[]): VNode {
  return h(
    url ? 'a' : 'span',
    {
      class: {
        site_notification: true,
        [n.type]: true,
        new: !n.read,
      },
      attrs: url ? { href: url } : undefined,
    },
    [
      h('i', {
        attrs: { 'data-icon': icon },
      }),
      h('span.content', content),
    ],
  );
}

function drawTime(n: Notification) {
  const date = new Date(n.date);
  return h(
    'time.timeago',
    {
      attrs: {
        title: date.toLocaleString(),
        datetime: n.date,
      },
    },
    window.lishogi.timeago.format(date),
  );
}

function userFullName(u?: LightUser) {
  if (!u) return i18n('anonymousUser');
  return u.title ? `${u.title} ${u.name}` : u.name;
}

function arrangementPath(n: Notification): string {
  const split = (n.content.id as string).split('/', 2);
  return split.length === 2 ? `${split[0]}#${split[1]}` : `${n.content.tid}#${n.content.id}`;
}
