import spinner from 'common/spinner';
import { i18n, i18nPluralSame } from 'i18n';
import { i18nVariant } from 'i18n/variant';
import { type VNode, h } from 'snabbdom';
import type {
  Challenge,
  ChallengeData,
  ChallengeDirection,
  ChallengeUser,
  Ctrl,
  TimeControl,
} from './interfaces';

export function loaded(ctrl: Ctrl): VNode {
  return ctrl.redirecting()
    ? h('div#challenge-app.dropdown', h('div.initiating', spinner()))
    : h('div#challenge-app.links.dropdown.rendered', renderContent(ctrl));
}

export function loading(): VNode {
  return h('div#challenge-app.links.dropdown.rendered', [h('div.empty.loading', '-'), create()]);
}

function renderContent(ctrl: Ctrl): VNode[] {
  const d = ctrl.data();
  const nb = d.in.length + d.out.length;
  return nb ? [allChallenges(ctrl, d, nb)] : [empty(), create()];
}

function userPowertips(vnode: VNode) {
  window.lishogi.powertip.manualUserIn(vnode.elm);
}

function allChallenges(ctrl: Ctrl, d: ChallengeData, nb: number): VNode {
  return h(
    'div.challenges',
    {
      class: { many: nb > 3 },
      hook: {
        insert: userPowertips,
        postpatch: userPowertips,
      },
    },
    d.in.map(challenge(ctrl, 'in')).concat(d.out.map(challenge(ctrl, 'out'))),
  );
}

function challenge(ctrl: Ctrl, dir: ChallengeDirection) {
  return (c: Challenge) => {
    return h(
      `div.challenge.${dir}.c-${c.id}${c.tourInfo ? '.tour-info' : ''}`,
      {
        class: {
          declined: !!c.declined,
        },
      },
      [
        h('div.content', [
          h('div.head', renderUser(dir === 'in' ? c.challenger : c.destUser)),
          h(
            'div.desc',
            c.tourInfo
              ? h(
                  'a',
                  { attrs: { href: `/tournament/${c.tourInfo.tourId}` } },
                  [
                    c.tourInfo.tourName || i18n('tournament'),
                    c.tourInfo.arrName || timeControl(c.timeControl),
                  ]
                    .filter(s => s)
                    .join(' - '),
                )
              : [
                  c.rated ? i18n('rated') : i18n('casual'),
                  timeControl(c.timeControl),
                  i18nVariant(c.variant.key),
                ].join(' - '),
          ),
        ]),
        h('i', {
          attrs: { 'data-icon': c.tourInfo ? 'g' : c.perf.icon },
        }),
        h('div.buttons', (dir === 'in' ? inButtons : outButtons)(ctrl, c)),
      ],
    );
  };
}

function inButtons(ctrl: Ctrl, c: Challenge): VNode[] {
  return [
    h(
      'form',
      {
        attrs: {
          method: 'post',
          action: `/challenge/${c.id}/accept`,
        },
      },
      [
        h('button.button.accept', {
          attrs: {
            type: 'submit',
            'data-icon': 'E',
            title: i18n('accept'),
          },
          hook: onClick(ctrl.onRedirect),
        }),
      ],
    ),
    h('button.button.decline', {
      attrs: {
        type: 'submit',
        'data-icon': 'L',
        title: i18n('decline'),
      },
      hook: onClick(() => ctrl.decline(c.id)),
    }),
  ];
}

function outButtons(ctrl: Ctrl, c: Challenge) {
  return [
    h('div.owner', [
      h('span.waiting', i18n('waiting')),
      h('a.view', {
        attrs: {
          'data-icon': 'v',
          href: `/${c.id}`,
          title: i18n('viewInFullSize'),
        },
      }),
    ]),
    h('button.button.decline', {
      attrs: {
        'data-icon': 'L',
        title: i18n('cancel'),
      },
      hook: onClick(() => ctrl.cancel(c.id)),
    }),
  ];
}

function timeControl(c: TimeControl): string {
  switch (c.type) {
    case 'unlimited':
      return i18n('unlimited');
    case 'correspondence':
      return i18nPluralSame('nbDays', c.daysPerTurn || 0);
    case 'clock':
      return c.show || '-';
  }
}

function renderUser(u?: ChallengeUser): VNode {
  if (!u) return h('span', 'Open challenge');
  const rating = u.rating + (u.provisional ? '?' : '');
  return h(
    'a.ulpt.user-link',
    {
      attrs: { href: `/@/${u.name}` },
      class: { online: !!u.online },
    },
    [
      h(`i.line${u.patron ? '.patron' : ''}`),
      h('name', [
        u.title &&
          h('span.title', u.title == 'BOT' ? { attrs: { 'data-bot': true } } : {}, `${u.title} `),
        `${u.name} (${rating}) `,
      ]),
      h(
        'signal',
        u.lag === undefined
          ? []
          : [1, 2, 3, 4].map(i =>
              h('i', {
                class: { off: u.lag! < i },
              }),
            ),
      ),
    ],
  );
}

function create(): VNode {
  return h('a.create', {
    attrs: {
      href: '/?any#friend',
      'data-icon': 'O',
      title: 'Challenge someone',
    },
  });
}

function empty(): VNode {
  return h(
    'div.empty.text',
    {
      attrs: {
        'data-icon': '',
      },
    },
    i18n('noChallenges'),
  );
}

function onClick(f: (e: Event) => void) {
  return {
    insert: (vnode: VNode) => {
      (vnode.elm as HTMLElement).addEventListener('click', f);
    },
  };
}
