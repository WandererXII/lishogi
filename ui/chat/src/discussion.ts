import { icons } from 'common/icons';
import * as enhance from 'common/rich-text';
import { i18n, i18nFormat, i18nFormatCapitalized } from 'i18n';
import { colorName } from 'shogi/color-name';
import { h, thunk, type VNode, type VNodeData } from 'snabbdom';
import type { ChatCtrl, Line } from './interfaces';
import { lineAction as modLineAction } from './moderation';
import { presetView } from './preset';
import * as spam from './spam';
import { userLink } from './util';
import { flag } from './xhr';

export default function (ctrl: ChatCtrl): Array<VNode | undefined> {
  if (!ctrl.vm.enabled) return [];
  const scrollCb = (vnode: VNode) => {
    const el = vnode.elm as HTMLElement;
    if (ctrl.data.lines.length > 5) {
      const autoScroll =
        el.scrollTop === 0 || el.scrollTop > el.scrollHeight - el.clientHeight - 100;
      if (autoScroll) {
        el.scrollTop = 999999;
        setTimeout((_: any) => {
          el.scrollTop = 999999;
        }, 300);
      }
    }
  };
  const mod = ctrl.moderation();
  const vnodes = [
    h(
      `ol.mchat__messages.chat-v-${ctrl.data.domVersion}`,
      {
        attrs: {
          role: 'log',
          'aria-live': 'polite',
          'aria-atomic': 'false',
        },
        hook: {
          insert(vnode) {
            const $el = $(vnode.elm as HTMLElement).on(
              'click',
              'a.jump',
              (e: JQuery.ClickEvent) => {
                window.lishogi.pubsub.emit(
                  'jump',
                  (e.target as HTMLElement).getAttribute('data-ply'),
                );
              },
            );
            if (mod)
              $el.on('click', '.mod', (e: JQuery.ClickEvent) =>
                mod.open((e.target as HTMLElement).parentNode as HTMLElement),
              );
            else
              $el.on('click', '.flag', (e: JQuery.ClickEvent) =>
                report(ctrl, (e.target as HTMLElement).parentNode as HTMLElement),
              );
            scrollCb(vnode);
          },
          postpatch: (_, vnode) => scrollCb(vnode),
        },
      },
      selectLines(ctrl).map(line => renderLine(ctrl, line)),
    ),
    renderInput(ctrl),
  ];
  const presets = presetView(ctrl.preset);
  if (presets) vnodes.push(presets);
  return vnodes;
}

function renderInput(ctrl: ChatCtrl): VNode | undefined {
  if (!ctrl.vm.writeable) return;
  if ((ctrl.data.loginRequired && !ctrl.data.userId) || ctrl.data.restricted)
    return h('input.mchat__say', {
      attrs: {
        placeholder: i18n('loginToChat'),
        disabled: true,
      },
    });
  let placeholder: string;
  if (ctrl.vm.timeout) placeholder = i18n('youHaveBeenTimedOut');
  else if (ctrl.opts.blind) placeholder = 'Chat';
  else if (ctrl.opts.playerFilter /*&& ctrl.isPlayer()*/)
    placeholder = `Players can't see your messages during game.`;
  else placeholder = i18n('talkInChat');
  return h('input.mchat__say', {
    attrs: {
      placeholder,
      autocomplete: 'off',
      maxlength: ctrl.data.maxLineLength,
      disabled: ctrl.vm.timeout || !ctrl.vm.writeable,
    },
    hook: {
      insert(vnode) {
        setupHooks(ctrl, vnode.elm as HTMLInputElement);
      },
    },
  });
}

let mouchListener: EventListener;

const setupHooks = (ctrl: ChatCtrl, chatEl: HTMLInputElement) => {
  const storage = window.lishogi.tempStorage.make('chatInput');
  if (storage.get()) {
    chatEl.value = storage.get()!;
    storage.remove();
    chatEl.focus();
  }

  chatEl.addEventListener('keypress', (e: KeyboardEvent) =>
    setTimeout(() => {
      const el = e.target as HTMLInputElement;
      const txt = el.value;
      storage.set(el.value);
      if (e.which == 10 || e.which == 13) {
        if (txt === '') $('.keyboard-move input').focus();
        else {
          spam.report(txt);
          if (spam.hasTeamUrl(txt)) alert("Please don't advertise teams in the chat.");
          else ctrl.post(txt);
          el.value = '';
          storage.remove();
        }
      } else {
        el.removeAttribute('placeholder');
      }
    }),
  );

  window.lishogi.mousetrap.bind('c', () => {
    chatEl.focus();
    return false;
  });

  // Ensure clicks remove chat focus.
  // See lichess-org/chessground#109

  const mouchEvents = ['touchstart', 'mousedown'];

  if (mouchListener)
    mouchEvents.forEach(event => {
      document.body.removeEventListener(event, mouchListener, { capture: true });
    });

  mouchListener = (e: MouseEvent) => {
    if (!e.shiftKey && e.buttons !== 2 && e.button !== 2) chatEl.blur();
  };

  chatEl.onfocus = () =>
    mouchEvents.forEach(event => {
      document.body.addEventListener(event, mouchListener, {
        passive: true,
        capture: true,
      });
    });

  chatEl.onblur = () =>
    mouchEvents.forEach(event => {
      document.body.removeEventListener(event, mouchListener, { capture: true });
    });
};

function lineFromNonPlayer(ctrl: ChatCtrl, line: Line): boolean {
  const lineUsersId = line.u?.toLowerCase();
  return (
    line.u !== 'lishogi' &&
    ctrl.opts.players?.sente !== lineUsersId &&
    ctrl.opts.players?.gote !== lineUsersId
  );
}

function sameLines(l1: Line, l2: Line) {
  return l1.d && l2.d && l1.u === l2.u;
}

function selectLines(ctrl: ChatCtrl): Array<Line> {
  let prev: Line;
  const ls: Array<Line> = [];
  ctrl.data.lines.forEach(line => {
    if (
      !line.d &&
      (!prev || !sameLines(prev, line)) &&
      (!line.r || (line.u || '').toLowerCase() == ctrl.data.userId) &&
      !spam.skip(line.t) &&
      (!ctrl.opts.playerFilter || !lineFromNonPlayer(ctrl, line))
    )
      ls.push(line);
    prev = line;
  });
  return ls;
}

const updateText = (parseMoves: boolean) => (oldVnode: VNode, vnode: VNode) => {
  if ((vnode.data as VNodeData).lishogiChat !== (oldVnode.data as VNodeData).lishogiChat) {
    (vnode.elm as HTMLElement).innerHTML = enhance.enhance(
      (vnode.data as VNodeData).lishogiChat,
      parseMoves,
    );
  }
};

function renderText(t: string, parseMoves: boolean, system: boolean) {
  const [timestamp, text] = system ? separateTimestamp(t) : [false, t];
  const maybeTranslated = system ? translateMessage(text) : text;
  if (enhance.isMoreThanText(text)) {
    const hook = updateText(parseMoves);
    return h('t', {
      attrs: {
        title: timestamp,
      },
      lishogiChat: maybeTranslated,
      hook: {
        create: hook,
        update: hook,
      },
    });
  }
  return h('t', { attrs: { title: timestamp } }, maybeTranslated);
}

function separateTimestamp(str: string): [string, string] {
  const match = str.match(/\[(.*?)\]/);
  return match ? [match[1], str.replace(/^\[.*?\]\s?/, '')] : ['', str];
}

function translateMessage(t: string): string {
  const [prefix, trans, colorStr, handicapStr] = t.split(':');
  const color = colorStr as Color;
  const handicap = handicapStr === 'true';
  if (prefix !== 'key') return t;
  else
    switch (trans) {
      case 'takebackPropositionAccepted':
        return i18n('takebackPropositionAccepted');
      case 'takebackPropositionSent':
        return i18n('takebackPropositionSent');
      case 'takebackPropositionCanceled':
        return i18n('takebackPropositionCanceled');
      case 'takebackPropositionDeclined':
        return i18n('takebackPropositionDeclined');
      case 'rematchOfferAccepted':
        return i18n('rematchOfferAccepted');
      case 'rematchOfferCanceled':
        return i18n('rematchOfferCanceled');
      case 'rematchOfferDeclined':
        return i18n('rematchOfferDeclined');
      case 'rematchOfferSent':
        return i18n('rematchOfferSent');
      case 'drawOfferAccepted':
        return i18n('drawOfferAccepted');
      case 'xOffersDraw':
        return i18nFormatCapitalized('xOffersDraw', colorName(color, handicap));
      case 'drawOfferCanceled':
        return i18n('drawOfferCanceled');
      case 'xDeclinesDraw':
        return i18nFormatCapitalized('xDeclinesDraw', colorName(color, handicap));
      case 'adjournmentOfferAccepted':
        return i18n('adjournmentOfferAccepted');
      case 'xOffersAdjournment':
        return i18nFormatCapitalized('xOffersAdjournment', colorName(color, handicap));
      case 'adjournmentOfferCanceled':
        return i18n('adjournmentOfferCanceled');
      case 'xDeclinesAdjournment':
        return i18nFormatCapitalized('xDeclinesAdjournment', colorName(color, handicap));
      case 'adjournmentOfferSent':
        return i18n('adjournmentOfferSent');
      case 'gameResumed':
        return i18n('gameResumed');
      case 'xOffersResumption':
        return i18nFormatCapitalized('xOffersResumption', colorName(color, handicap));
      case 'resumptionOfferCanceled':
        return i18n('resumptionOfferCanceled');
      case 'xDeclinesResumption':
        return i18nFormatCapitalized('xDeclinesResumption', colorName(color, handicap));
      case 'gameAborted':
        return i18n('gameAborted');
      case 'xPlayedIllegalMove':
        return i18nFormat('xPlayedIllegalMove', colorName(color, handicap));
      default:
        console.warn('Unhandled translation', t);
        return i18n(trans as I18nKeyBasic);
    }
}

function report(ctrl: ChatCtrl, line: HTMLElement) {
  const userA = line.querySelector('a.user-link') as HTMLLinkElement;
  const text = (line.querySelector('t') as HTMLElement).innerText;
  if (userA && confirm(`Report "${text}" to moderators?`))
    flag(ctrl.data.resourceId, userA.href.split('/')[4], text);
}

function renderLine(ctrl: ChatCtrl, line: Line) {
  const system = line.u === 'lishogi';
  const textNode = renderText(line.t, ctrl.opts.parseMoves, system);

  if (system) return h('li.system', textNode);

  if (line.c)
    return h('li.player', [h('span.color', colorName(line.c, !!ctrl.opts.handicap)), textNode]);

  const userNode = thunk('a', line.u, userLink, [line.u]);
  const lineUsersId = line.u?.toLowerCase();

  return h(
    'li',
    ctrl.opts.withColorTags
      ? {
          attrs: {
            style: `border-color:${userIdToHexColor(lineUsersId)}`,
          },
        }
      : {
          class: {
            'color-icon': true,
            me: ctrl.opts.data.userId === lineUsersId,
            sente: ctrl.opts.players?.sente === lineUsersId,
            gote: ctrl.opts.players?.gote === lineUsersId,
          },
        },
    ctrl.moderation()
      ? [lineUsersId ? modLineAction() : null, userNode, textNode]
      : [
          ctrl.data.userId && lineUsersId && ctrl.data.userId != lineUsersId
            ? h('i.flag', {
                attrs: {
                  'data-icon': icons.warning,
                  title: 'Report',
                },
              })
            : null,
          userNode,
          textNode,
        ],
  );
}

function userIdToHexColor(userId: string | undefined): string {
  userId = userId || '';
  let hash = 0;
  for (let i = 0; i < userId.length; i++) {
    hash = userId.charCodeAt(i) + ((hash << 5) - hash);
  }

  const color = (hash & 0x00ffffff).toString(16).padStart(6, '0');
  return `#${color}`;
}
