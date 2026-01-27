import * as domData from 'common/data';
import { initOneWithState } from 'common/mini-board';
import { reverse } from 'common/string';
import type { Api as ShogigroundApi } from 'shogiground/api';
import * as compat from 'shogiops/compat';
import { Hand, Hands } from 'shogiops/hands';
import { makeSfen, parseSfen } from 'shogiops/sfen';
import type { Result, Role } from 'shogiops/types';
import * as util from 'shogiops/variant/util';

const readServerValue = (t: string): string => atob(reverse(t));

window.lishogi.ready.then(() => {
  setTimeout(() => {
    document.querySelectorAll('div.captcha').forEach((captchaEl: HTMLElement) => {
      const board = captchaEl.querySelector<HTMLElement>('.mini-board')!;
      const hint = readServerValue(board.dataset.x!) as Key;
      const orientation = readServerValue(board.dataset.y!) as Color;

      let sfen = readServerValue(board.dataset.z!);

      const pos = parseSfen('minishogi', sfen, false);
      if (pos.isOk) {
        const senteHand = orientation === 'gote' ? pos.value.hands.color('sente') : Hand.empty();
        const goteHand = orientation === 'sente' ? pos.value.hands.color('gote') : Hand.empty();
        pos.value.hands = Hands.from(senteHand, goteHand);
        sfen = makeSfen(pos.value);
      }

      initOneWithState(board, {
        variant: 'minishogi',
        sfen,
        orientation,
        playable: true,
      });
      const sg = domData.get<ShogigroundApi>(board, 'shogiground')!;

      const input = captchaEl.querySelector<HTMLInputElement>('input')!;
      input.value = '';

      const dests = pos.isOk ? compat.shogigroundMoveDests(pos.value) : new Map();

      sg.set({
        activeColor: sg.state.orientation,
        turnColor: sg.state.orientation,
        movable: {
          free: pos.isErr,
          dests,
          events: {
            after: (orig: Key, dest: Key) => {
              captchaEl.classList.remove('success', 'failure');
              submit(`${orig}${dest}`);
            },
          },
        },
        droppable: {
          free: false,
        },
        hands: {
          inlined: true,
          roles: util.handRoles('minishogi'),
        },
      });

      const submit = (solution: string) => {
        input.value = solution;
        window.lishogi.xhr
          .text('GET', captchaEl.dataset.checkUrl!, { url: { solution } })
          .then(data => {
            const isSuccess = data == '1';
            captchaEl.classList.add(isSuccess ? 'success' : 'failure');
            if (isSuccess) {
              sg.setSquareHighlights([]);

              const key = solution.slice(2, 4) as Key;
              const piece = sg.state.pieces.get(key)!;
              const sfenStr = `${sg.getBoardSfen()} ${piece.color === 'sente' ? ' w' : ' b'}`;
              const pos = parseSfen('minishogi', sfenStr, false);
              const outcome = pos.isOk ? pos.value.outcome()?.result : undefined;
              const winResult: Result[] = ['checkmate', 'stalemate'];
              if (outcome && winResult.includes(outcome)) {
                sg.setPieces(
                  new Map([
                    [
                      key,
                      {
                        color: piece.color,
                        role: util.promote('minishogi')(piece.role as Role) || piece.role,
                        promoted: true,
                      },
                    ],
                  ]),
                );
              }
              sg.stop();
            } else
              setTimeout(() => {
                sg.set({
                  sfen: {
                    board: sfen,
                  },
                  turnColor: sg.state.orientation,
                  movable: {
                    dests: dests,
                  },
                });
                sg.setSquareHighlights([{ key: hint, className: 'help' }]);
              }, 300);
          });
      };
    });
  });
});
