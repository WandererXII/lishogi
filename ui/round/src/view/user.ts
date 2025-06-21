import type { Player } from 'game/interfaces';
import { i18n, i18nFormat } from 'i18n';
import { colorName } from 'shogi/color-name';
import { engineNameFromCode } from 'shogi/engine-name';
import { isHandicap } from 'shogiops/handicaps';
import { type VNode, h } from 'snabbdom';
import type RoundController from '../ctrl';
import type { Position } from '../interfaces';

export function userHtml(ctrl: RoundController, player: Player, position: Position): VNode {
  const d = ctrl.data;
  const user = player.user;
  const perf = user ? user.perfs[d.game.perf] : null;
  const rating = player.rating ? player.rating : perf?.rating;
  const rd = player.ratingDiff;
  const ratingDiff =
    rd === 0
      ? h('span', '±0')
      : rd && rd > 0
        ? h('good', `+${rd}`)
        : rd && rd < 0
          ? h('bad', `−${-rd}`)
          : undefined;
  const handicap = isHandicap({
    rules: ctrl.data.game.variant.key,
    sfen: ctrl.data.game.initialSfen,
  });

  if (user) {
    const connecting = !player.onGame && ctrl.firstSeconds && user.online;
    return h(
      `div.ruser-${position}.ruser.user-link`,
      {
        class: {
          online: player.onGame,
          offline: !player.onGame,
          long: user.username.length > 16,
          connecting,
        },
      },
      [
        h(
          `div.color-icon.${player.color}`,
          {
            attrs: {
              title: colorName(player.color, handicap),
            },
          },
          [],
        ),
        h(`i.line${user.patron ? '.patron' : ''}`, {
          attrs: {
            title: connecting
              ? i18n('connectingToTheGame')
              : player.onGame
                ? i18nFormat('xJoinedTheGame', user.username)
                : i18nFormat('xLeftTheGame', user.username),
          },
        }),
        h(
          'a.text.ulpt',
          {
            attrs: {
              'data-pt-pos': 's',
              href: `/@/${user.username}`,
              target: ctrl.isPlaying() ? '_blank' : '_self',
            },
          },
          user.title
            ? [
                h(
                  'span.title',
                  user.title == 'BOT' ? { attrs: { 'data-bot': true } } : {},
                  user.title,
                ),
                ' ',
                user.username,
              ]
            : [user.username],
        ),
        rating ? h('rating', rating + (player.provisional ? '?' : '')) : null,
        ratingDiff,
        player.engine
          ? h('span', {
              attrs: {
                'data-icon': 'j',
                title: i18n('thisAccountViolatedTos'),
              },
            })
          : null,
      ],
    );
  }
  const connecting = !player.onGame && ctrl.firstSeconds;
  return h(
    `div.ruser-${position}.ruser.user-link`,
    {
      class: {
        online: player.onGame,
        offline: !player.onGame,
        connecting,
      },
    },
    [
      h(
        `div.color-icon.${player.color}`,
        {
          attrs: {
            title: colorName(player.color, handicap),
          },
        },
        [],
      ),
      h('i.line', {
        attrs: {
          title: connecting
            ? i18n('connectingToTheGame')
            : player.onGame
              ? i18nFormat('xJoinedTheGame', i18n('player'))
              : i18nFormat('xLeftTheGame', i18n('player')),
        },
      }),
      h(
        'name',
        player.ai
          ? engineNameFromCode(player.aiCode)
          : player.name || h('span.anon', i18n('anonymousUser')),
      ),
      player.ai ? h('div.ai-level', i18nFormat('levelX', player.ai)) : null,
    ],
  );
}

export function userTxt(player: Player): string {
  if (player.user) {
    return (player.user.title ? `${player.user.title} ` : '') + player.user.username;
  } else if (player.ai) return engineNameFromCode(player.aiCode);
  else return i18n('anonymousUser');
}
