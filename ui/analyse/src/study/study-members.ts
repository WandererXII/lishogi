import { type Prop, prop } from 'common/common';
import { icons } from 'common/icons';
import { bind, dataIcon, flagImage, onInsert } from 'common/snabbdom';
import { i18n } from 'i18n';
import { h, type VNode } from 'snabbdom';
import { iconTag, scrollTo, titleNameToId } from '../util';
import type {
  StudyCtrl,
  StudyData,
  StudyMember,
  StudyMemberMap,
  StudyMembersCtrl,
  ToolTab,
} from './interfaces';
import { makeCtrl as inviteFormCtrl } from './invite-form';
import type { NotifCtrl } from './notif';

interface Opts {
  initDict: StudyMemberMap;
  myId: string | null;
  ownerId: string;
  send: Socket.Send;
  tab: Prop<ToolTab>;
  notif: NotifCtrl;
  onBecomingContributor(): void;
  admin: boolean;
  redraw(): void;
}

function memberActivity(onIdle: () => void) {
  let timeout: Timeout;
  const schedule = () => {
    if (timeout) clearTimeout(timeout);
    timeout = setTimeout(onIdle, 300);
  };
  schedule();
  return schedule;
}

export function ctrl(opts: Opts): StudyMembersCtrl {
  const dict = prop<StudyMemberMap>(opts.initDict);
  const active: { [id: string]: () => void } = {};
  let online: { [id: string]: boolean } = {};
  let spectatorIds: string[] = [];
  const max = 30;

  function owner() {
    return dict()[opts.ownerId];
  }

  function isOwner() {
    return opts.myId === opts.ownerId;
  }

  function isOwnerOrAdmin() {
    return isOwner() || (opts.admin && canContribute());
  }

  function myMember() {
    return opts.myId ? dict()[opts.myId] : null;
  }

  function canContribute(): boolean {
    const m = myMember();
    return !!m && m.role === 'w';
  }

  const inviteForm = inviteFormCtrl(opts.send, dict, () => opts.tab('members'), opts.redraw);

  function setActive(id: string) {
    if (opts.tab() !== 'members') return;
    if (active[id]) active[id]();
    else
      active[id] = memberActivity(() => {
        delete active[id];
        opts.redraw();
      });
    opts.redraw();
  }

  function updateOnline() {
    online = {};
    const members: StudyMemberMap = dict();
    spectatorIds.forEach(id => {
      if (members[id]) online[id] = true;
    });
    if (opts.tab() === 'members') opts.redraw();
  }

  return {
    dict,
    myId: opts.myId,
    inviteForm,
    update(members: StudyMemberMap) {
      const wasViewer = myMember() && !canContribute();
      const wasContrib = myMember() && canContribute();
      dict(members);
      if (wasViewer && canContribute()) {
        opts.onBecomingContributor();
        opts.notif.set({
          text: i18n('study:youAreNowAContributor'),
          duration: 3000,
        });
      } else if (wasContrib && !canContribute())
        opts.notif.set({
          text: i18n('study:youAreNowASpectator'),
          duration: 3000,
        });
      updateOnline();
    },
    setActive,
    isActive(id: string) {
      return !!active[id];
    },
    owner,
    myMember,
    isOwner,
    isOwnerOrAdmin,
    canContribute,
    max,
    setRole(id: string, role) {
      setActive(id);
      opts.send('setRole', {
        userId: id,
        role,
      });
    },
    kick(id: string) {
      opts.send('kick', id);
    },
    leave() {
      opts.send('leave');
    },
    ordered() {
      const d = dict();
      return Object.keys(d)
        .map(id => d[id])
        .sort((a, _b) => (a.user.id === this.myId ? -1 : 0));
    },
    size() {
      return Object.keys(dict()).length;
    },
    setSpectators(usernames?: string[]) {
      const names = usernames || [];
      this.inviteForm.setSpectators(names);
      spectatorIds = names.map(titleNameToId);
      updateOnline();
    },
    isOnline(userId: string) {
      return online[userId];
    },
    hasOnlineContributor() {
      const members = dict();
      for (const i in members) if (online[i] && members[i].role === 'w') return true;
      return false;
    },
  };
}

function isPostGameStudyPlayer(data: StudyData, userId: string): boolean {
  return (
    data.postGameStudy?.players.sente.userId === userId ||
    data.postGameStudy?.players.gote.userId === userId
  );
}

export function view(ctrl: StudyCtrl): VNode {
  const members = ctrl.members;
  const isOwner = members.isOwner();
  const canInvite =
    members.isOwnerOrAdmin() ||
    (ctrl.data.postGameStudy?.withOpponent && isPostGameStudyPlayer(ctrl.data, members.myId));

  function username(member: StudyMember) {
    const u = member.user;
    return h(
      'span.user-link.ulpt',
      {
        attrs: { 'data-href': `/@/${u.name}` },
      },
      [u.name, u.countryCode ? flagImage(u.countryCode) : undefined],
    );
  }

  function statusIcon(member: StudyMember) {
    const contrib = member.role === 'w';
    return h(
      'span.status',
      {
        class: {
          contrib,
          active: members.isActive(member.user.id),
          online: members.isOnline(member.user.id),
        },
        attrs: {
          title: contrib ? i18n('study:contributor') : i18n('study:spectator'),
        },
      },
      [iconTag(contrib ? icons.person : icons.view)],
    );
  }

  function memberConfig(member: StudyMember): VNode {
    const roleId = `member-role-${member.user.id}`;
    const canUpdateMembers =
      (isOwner || (canInvite && !isPostGameStudyPlayer(ctrl.data, member.user.id))) &&
      member.user.id !== members.myId;

    return h(
      'm-config',
      {
        key: `${member.user.id}-config`,
        hook: onInsert(el => scrollTo($(el).parent('.members')[0] as HTMLElement, el)),
      },
      canUpdateMembers
        ? [
            h('div.role', [
              h('label', { attrs: { for: roleId } }, i18n('study:contributor')),
              h('div.switch', [
                h('input.cmn-toggle', {
                  attrs: {
                    id: roleId,
                    type: 'checkbox',
                    checked: member.role === 'w',
                  },
                  hook: bind(
                    'change',
                    e => {
                      members.setRole(
                        member.user.id,
                        (e.target as HTMLInputElement).checked ? 'w' : 'r',
                      );
                    },
                    ctrl.redraw,
                  ),
                }),
                h('label', { attrs: { for: roleId } }),
              ]),
            ]),
            h(
              'div.kick',
              h('a.button', {
                attrs: dataIcon(icons.cancel),
                title: i18n('study:kick'),
                hook: bind(
                  'click',
                  _ => {
                    members.kick(member.user.id);
                  },
                  ctrl.redraw,
                ),
              }),
            ),
          ]
        : !isOwner && member.user.id === members.myId
          ? [
              h('act.leave', {
                key: 'leave',
                attrs: {
                  'data-icon': icons.exit,
                  title: i18n('study:leaveTheStudy'),
                },
                hook: bind('click', members.leave, ctrl.redraw),
              }),
            ]
          : [],
    );
  }

  const ordered = members.ordered();

  return h(
    'div.study__members',
    {
      hook: {
        insert: _ => {
          window.lishogi.pubsub.emit('content_loaded');
          window.lishogi.pubsub.emit('chat.resize');
        },
      },
    },
    [
      ...ordered.map((member: any) => {
        return h(
          'div.member',
          {
            key: member.user.id,
          },
          [h('div.left', [statusIcon(member), username(member)]), memberConfig(member)],
        );
      }),
      canInvite && ordered.length < members.max
        ? h(
            'div.add',
            h(
              'button.button.text',
              {
                key: 'add',
                attrs: dataIcon(icons.createNew),
                hook: bind('click', members.inviteForm.toggle, ctrl.redraw),
              },
              i18n('study:addMembers'),
            ),
          )
        : null,
      !members.canContribute() && ctrl.data.admin
        ? h(
            'form.admin',
            {
              attrs: {
                method: 'post',
                action: `/study/${ctrl.data.id}/admin`,
              },
            },
            [
              h(
                'button.button.button-red.button-thin',
                {
                  attrs: { type: 'submit' },
                },
                'Enter as admin',
              ),
            ],
          )
        : null,
    ],
  );
}
