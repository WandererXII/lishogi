import { capitalize } from 'common/string';
import { colorName } from 'shogi/color-name';
import { COLORS } from 'shogiops/constants';
import { handRoles } from 'shogiops/variant/util';

interface CoordinatesOpts {
  scoreUrl: string;
  points: {
    sente: number[];
    gote: number[];
  };
}

type ColorPref = Color | 'random';

const duration: number = 30 * 1000;
const tickDelay: number = 50;

function main(opts: CoordinatesOpts): void {
  const $trainer = $('#trainer');
  const $board = $('.coord-trainer__board .sg-wrap');
  const $side = $('.coord-trainer__side');
  const $right = $('.coord-trainer__table');
  const $button = $('.coord-trainer__button');
  const $currentColor = $button.find('.current-color');
  const $bar = $trainer.find('.progress_bar');
  const $coords = [$('#next_coord0'), $('#next_coord1')];
  const $start = $button.find('.start');
  const $explanation = $right.find('.explanation');
  const $score = $('.coord-trainer__score');
  const $timer = $('.coord-trainer__timer');

  const scoreUrl = opts.scoreUrl;
  const notationPref = Number.parseInt(document.body.dataset.notation || '0');
  const colorStorage = window.lishogi.storage.make('coordinate.color2');

  let ground: ReturnType<typeof window.Shogiground>;

  let color: Color;
  let startAt: Date;
  let score: number;
  let wrongTimeout: Timeout;

  $board.removeClass('preload');
  const showColor = () => {
    const colorPref = colorStorage.get() || 'random';
    const colorWithFallback: ColorPref = (COLORS as readonly string[]).includes(colorPref)
      ? (colorPref as Color)
      : 'random';

    color = colorWithFallback === 'random' ? COLORS[Math.round(Math.random())] : colorWithFallback;
    if (!ground)
      ground = window.Shogiground(
        {
          activeColor: undefined,
          hands: {
            roles: handRoles('standard'),
            inlined: true,
          },
          coordinates: { enabled: false },
          blockTouchScroll: true,
          drawable: { enabled: false },
          movable: {
            free: false,
          },
          highlight: {
            hovered: true,
          },
          orientation: color,
        },
        {
          board: $board[0] as HTMLElement,
        },
      );
    else if (color !== ground.state.orientation) ground.toggleOrientation();
    $trainer.removeClass('sente gote').addClass(color);
    $currentColor.text(capitalize(colorName(color, false)));
  };
  showColor();

  $trainer.find<HTMLFormElement>('form.color').each(function (this: HTMLFormElement) {
    const $form = $(this);

    const stored = colorStorage.get();
    const map = { sente: 0, random: 1, gote: 2 } as const;
    const val = map[stored as ColorPref] ?? map.random;
    $form.find(`input[value="${val}"]`).prop('checked', true);

    $form.find('input').on('change', () => {
      const selected: string = $form.find<HTMLInputElement>('input:checked').val()!;
      const c = {
        0: 'sente',
        1: 'random',
        2: 'gote',
      }[selected]!;
      colorStorage.set(c);
      return false;
    });
  });

  const showCharts = () => {
    $side.find<HTMLCanvasElement>('.user_chart').each(function (
      this: HTMLCanvasElement,
      index: number,
    ) {
      const isSente = index === 0;
      const data = isSente ? opts.points.sente : opts.points.gote;
      (window.lishogi.modules as any).chartCoordinate(this, data);
    });
  };
  showCharts();

  const clearCoords = () => {
    $.each($coords, (_i, e) => {
      e.text('');
    });
  };

  const newCoord = (prevCoord: string) => {
    // disallow the previous coordinate's row or file from being selected
    let files = '123456789';
    const fileIndex = files.indexOf(prevCoord[0]);
    files = files.slice(0, fileIndex) + files.slice(fileIndex + 1, 9);

    let rows = '123456789';
    const rowIndex = rows.indexOf(prevCoord[1]);
    rows = rows.slice(0, rowIndex) + rows.slice(rowIndex + 1, 9);

    return codeCoords(
      files[Math.round(Math.random() * (files.length - 1))] +
        rows[Math.round(Math.random() * (rows.length - 1))],
    );
  };

  const advanceCoords = () => {
    $('#next_coord0').removeClass('nope');
    const lastElement = $coords.shift()!;
    $.each($coords, (i, e) => {
      e.attr('id', `next_coord${i}`);
    });
    lastElement.attr('id', `next_coord${$coords.length}`);
    lastElement.text(newCoord($coords[$coords.length - 1].text()));
    $coords.push(lastElement);
  };

  const stop = () => {
    clearCoords();
    $trainer.removeClass('play');
    $trainer.removeClass('wrong');
    $currentColor.text('');
    ground.set({
      events: {
        select: undefined,
      },
    });
    if (scoreUrl)
      window.lishogi.xhr
        .text('POST', scoreUrl, {
          formData: {
            color: color,
            score: score,
          },
        })
        .then(html => {
          $side.find('.scores').html(html);
          opts.points[color].push(score);
          showCharts();
        });
  };

  const tick = () => {
    const spent = Math.min(duration, Date.now() - startAt.getTime());
    const left = ((duration - spent) / 1000).toFixed(1);
    if (+left < 10) {
      $timer.addClass('hurry');
    }
    $timer.text(left);
    $bar.css('width', `${(100 * spent) / duration}%`);
    if (spent < duration) setTimeout(tick, tickDelay);
    else stop();
  };

  function codeCoords(key: string) {
    const rankMap1: Record<string, string> = {
      1: '一',
      2: '二',
      3: '三',
      4: '四',
      5: '五',
      6: '六',
      7: '七',
      8: '八',
      9: '九',
    };
    const rankMap2: Record<string, string> = {
      1: 'a',
      2: 'b',
      3: 'c',
      4: 'd',
      5: 'e',
      6: 'f',
      7: 'g',
      8: 'h',
      9: 'i',
    };
    const rankMap3: Record<string, string> = {
      1: '子',
      2: '丑',
      3: '寅',
      4: '卯',
      5: '辰',
      6: '巳',
      7: '午',
      8: '未',
      9: '申',
    };
    switch (notationPref) {
      // 11
      case 0:
      case 1:
        return key;
      // 1一
      case 2:
        return key[0] + rankMap1[key[1]];
      case 6:
        return rankMap3[key[0]] + rankMap1[key[1]];
      default:
        return key[0] + rankMap2[key[1]];
    }
  }

  $start.on('click', () => {
    $explanation.remove();
    $trainer.addClass('play').removeClass('init');
    $timer.removeClass('hurry');
    showColor();
    clearCoords();
    score = 0;
    $score.text(score);
    $bar.css('width', 0);
    setTimeout(() => {
      startAt = new Date();
      ground.set({
        events: {
          select: (key: string) => {
            const hit =
              codeCoords(key[0] + (key.charCodeAt(1) - 96).toString()) == $coords[0].text();
            if (hit) {
              score++;
              $score.text(score);
              advanceCoords();
            } else {
              clearTimeout(wrongTimeout);
              $trainer.addClass('wrong');

              wrongTimeout = setTimeout(() => {
                $trainer.removeClass('wrong');
              }, 500);
            }
          },
        },
      });
      $coords[0].text(newCoord('a1'));
      for (let i = 1; i < $coords.length; i++) $coords[i].text(newCoord($coords[i - 1].text()));
      tick();
    }, 1000);
  });
}

window.lishogi.registerModule(__bundlename__, main);
