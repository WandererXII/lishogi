import { sparkline } from '@fnando/sparkline';
import * as xhr from 'common/xhr';
import throttle from 'common/throttle';
import { Api as SgApi } from 'shogiground/api';
import { ColorChoice, TimeControl, CoordinateTrainerConfig, InputMethod, Mode, ModeScores, Redraw } from './interfaces';

const orientationFromColorChoice = (colorChoice: ColorChoice): Color =>
  (colorChoice === 'random' ? ['sente', 'gote'][Math.round(Math.random())] : colorChoice) as Color;

const newKey = (oldKey: Key | ''): Key => {
  // disallow the previous coordinate's row or file from being selected
  const files = '987654321'.replace(oldKey[0], '');
  const ranks = 'abcdefghi'.replace(oldKey[1], '');
  return (files[Math.floor(Math.random() * files.length)] + ranks[Math.floor(Math.random() * ranks.length)]) as Key;
};

const targetSvg = (target: 'current' | 'next'): string => `
<g transform="translate(50, 50)">
  <rect class="${target}-target" fill="none" stroke-width="10" x="-50" y="-50" width="100" height="100" rx="5" />
</g>
`;

export const DURATION = 30 * 1000;
const TICK_DELAY = 50;

export default class CoordinateTrainerCtrl {
  shogiground: SgApi | undefined;
  colorChoice: ColorChoice;
  config: CoordinateTrainerConfig;
  coordinateInputMethod: InputMethod;
  currentKey: Key | '' = '1a';
  hasPlayed = false;
  isAuth: boolean;
  keyboardInput: HTMLInputElement;
  mode: Mode;
  modeScores: ModeScores;
  nextKey: Key | '' = newKey('1a');
  orientation: Color;
  playing = false;
  redraw: Redraw;
  score = 0;
  timeAtStart: Date;
  timeControl: TimeControl;
  timeLeft = DURATION;
  trans: Trans;
  wrong: boolean;
  wrongTimeout: number;
  zen: boolean;

  constructor(config: CoordinateTrainerConfig, redraw: Redraw) {
    this.config = config;
    this.colorChoice = (window.lishogi.storage.get('coordinateTrainer.colorChoice') as ColorChoice) || 'random';
    this.timeControl =
      (window.lishogi.storage.get('coordinateTrainer.timeControl') as TimeControl) ||
      (document.body.classList.contains('kid') ? 'untimed' : 'thirtySeconds');
    this.orientation = orientationFromColorChoice(this.colorChoice);
    this.modeScores = config.scores;

    // Assume a smaller viewport means mobile, and default to buttons
    const savedInputMethod = window.lishogi.storage.get('coordinateTrainer.coordinateInputMethod');
    if (savedInputMethod) this.coordinateInputMethod = savedInputMethod as InputMethod;
    else this.coordinateInputMethod = window.innerWidth >= 980 ? 'text' : 'buttons';

    this.isAuth = document.body.hasAttribute('data-user');
    this.trans = window.lishogi.trans(this.config.i18n);
    this.redraw = redraw;

    if (window.location.hash.length == 5) {
      this.mode = window.location.hash === '#name' ? 'nameSquare' : 'findSquare';
    } else {
      this.mode = window.lishogi.storage.get('coordinateTrainer.mode') === 'nameSquare' ? 'nameSquare' : 'findSquare';
    }
    this.saveMode();

    const setZen = throttle(1000, zen =>
      xhr.text('/pref/zen', {
        method: 'post',
        body: xhr.form({ zen: zen ? 1 : 0 }),
      })
    );

    window.lishogi.pubsub.on('zen', () => {
      const zen = $('body').toggleClass('zen').hasClass('zen');
      window.dispatchEvent(new Event('resize'));
      setZen(zen);
    });

    $('#zentog').on('click', () => window.lishogi.pubsub.emit('zen'));
    window.Mousetrap.bind('z', () => window.lishogi.pubsub.emit('zen'));

    window.Mousetrap.bind('enter', () => (this.playing ? null : this.start()));

    window.addEventListener('resize', () => requestAnimationFrame(this.updateCharts), true);
  }

  setMode = (m: Mode) => {
    if (this.mode === m) return;
    this.mode = m;
    this.saveMode();
    this.redraw();
    this.updateCharts();
  };

  saveMode = () => {
    window.location.hash = `#${this.mode.substring(0, 4)}`;
    window.lishogi.storage.set('coordinateTrainer.mode', this.mode);
  };

  setColorChoice = (c: ColorChoice) => {
    if (this.colorChoice === c) return;
    this.colorChoice = c;
    this.setOrientation(orientationFromColorChoice(c));
    window.lishogi.storage.set('coordinateTrainer.colorChoice', this.colorChoice);
  };

  setOrientation = (o: Color) => {
    this.orientation = o;
    if (this.shogiground!.state.orientation !== o) this.shogiground!.toggleOrientation();
    this.redraw();
  };

  setTimeControl = (c: TimeControl) => {
    if (this.timeControl === c) return;
    this.timeControl = c;
    window.lishogi.storage.set('coordinateTrainer.timeControl', this.timeControl);
    this.redraw();
  };

  timeDisabled = () => this.timeControl === 'untimed';

  toggleInputMethod = () => {
    if (this.coordinateInputMethod === 'text') this.coordinateInputMethod = 'buttons';
    else this.coordinateInputMethod = 'text';
    this.redraw();
    window.lishogi.storage.set('coordinateTrainer.coordinateInputMethod', this.coordinateInputMethod);
  };

  start = () => {
    this.playing = true;
    this.hasPlayed = true;
    this.score = 0;
    this.timeLeft = DURATION;
    this.currentKey = '';
    this.nextKey = '';

    // Redraw the shogiground to remove the resize handle
    //this.shogiground?.redrawAll();

    // In case random is selected, recompute orientation
    this.setOrientation(orientationFromColorChoice(this.colorChoice));

    if (this.mode === 'nameSquare') this.keyboardInput.focus();

    setTimeout(() => {
      // Advance coordinates twice in order to get an entirely new set
      this.advanceCoordinates();
      this.advanceCoordinates();

      this.timeAtStart = new Date();
      if (!this.timeDisabled()) this.tick();
    }, 1000);
  };

  private tick = () => {
    const timeSpent = Math.min(DURATION, new Date().getTime() - +this.timeAtStart);
    this.timeLeft = DURATION - timeSpent;
    this.redraw();

    if (this.timeLeft > 0) setTimeout(this.tick, TICK_DELAY);
    else this.stop();
  };

  advanceCoordinates = () => {
    this.currentKey = this.nextKey;
    this.nextKey = newKey(this.nextKey);

    if (this.mode === 'nameSquare')
      this.shogiground?.setShapes([
        {
          orig: this.currentKey as Key,
          dest: this.currentKey as Key,
          customSvg: targetSvg('current'),
          brush: 'current',
        },
        { orig: this.nextKey as Key, dest: this.nextKey as Key, customSvg: targetSvg('next'), brush: 'next' },
      ]);

    this.redraw();
  };

  codeCoords = key => {
    const notation = $('.notation-3')[0] ? 3 : $('.notation-2')[0] ? 2 : 0;
    switch (notation) {
      case 3:
        return key[0] + 'abcdefghi'[key[1] - 1];
      case 2:
        return key[0] + '一二三四五六七八九'[key[1] - 1];
      default:
        return key;
    }
  };

  stop = () => {
    this.playing = false;
    this.wrong = false;

    if (this.mode === 'nameSquare') {
      this.keyboardInput.blur();
      this.keyboardInput.value = '';
    }

    if (this.timeControl === 'thirtySeconds') {
      this.updateScoreList();
      if (this.isAuth)
        xhr.text('/training/coordinate/score', {
          method: 'post',
          body: xhr.form({ mode: this.mode, color: this.orientation, score: this.score }),
        });
    }

    this.shogiground?.setShapes([]);
    //this.shogiground?.redrawAll();
    this.redraw();
  };

  updateScoreList = () => {
    // we only ever display the last 20 scores
    const scoreList = this.modeScores[this.mode][this.orientation];
    if (scoreList.length >= 20) this.modeScores[this.mode][this.orientation] = scoreList.slice(1, 20);
    this.modeScores[this.mode][this.orientation].push(this.score);
    requestAnimationFrame(() => this.updateCharts());
  };

  updateCharts = () => {
    for (const color of ['sente', 'gote'] as Color[]) {
      const svgElement = document.getElementById(`${color}-sparkline`);
      if (!svgElement) continue;
      this.updateChart(svgElement as unknown as SVGSVGElement, color);
    }
  };

  updateChart = (svgElement: SVGSVGElement, color: Color) => {
    const parent = svgElement.parentElement as HTMLDivElement;
    svgElement.setAttribute('width', `${parent.offsetWidth}px`);
    sparkline(svgElement, this.modeScores[this.mode][color], { interactive: true });
  };

  hasModeScores = (): boolean => this.modeScores[this.mode].sente.length + this.modeScores[this.mode].gote.length > 0;

  handleCorrect = () => {
    this.score++;
    this.advanceCoordinates();
    this.wrong = false;
  };

  handleWrong = () => {
    clearTimeout(this.wrongTimeout);
    this.wrong = true;
    this.redraw();

    this.wrongTimeout = setTimeout(() => {
      this.wrong = false;
      this.redraw();
    }, 500);
  };

  onShogigroundSelect = (key: Key) => {
    if (!this.playing || this.mode !== 'findSquare') return;

    if (key === this.currentKey) this.handleCorrect();
    else this.handleWrong();
  };

  onRadioInputKeyUp = (e: KeyboardEvent) => {
    // Mousetrap by default ignores key presses on inputs
    // when enter is pressed on a radio input, start training
    if (!this.playing && e.which === 13) this.start();
  };

  onKeyboardInputKeyUp = (e: KeyboardEvent) => {
    // normalize input value
    const input = e.target as HTMLInputElement;
    input.value = input.value.toLowerCase().replace(/[^a-i1-9]/, '');

    if (!e.isTrusted || !this.playing) {
      input.value = '';
      if (e.which === 13) this.start();
    } else this.checkKeyboardInput();
  };

  checkKeyboardInput = () => {
    const input = this.keyboardInput;
    if (input.value.length === 1) {
      // clear input if it begins with anything other than a file
      input.value = input.value.replace(/[^1-9]/, '');
    } else if (input.value.length === 2 && input.value === this.currentKey) {
      input.value = '';
      this.handleCorrect();
    } else if (input.value.length === 2 && !input.value.match(/[1-9][a-i]/)) {
      // if they've entered e.g. "12", remove the "1"
      input.value = input.value[1];
    } else if (input.value.length >= 2) {
      input.value = '';
      this.handleWrong();
    }
  };
}
