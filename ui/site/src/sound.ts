import { assetUrl } from 'common/assets';
import { defined, useJapanese } from 'common/common';
import throttle from 'common/throttle';
import { storage } from './storage';

export function createSound(): SoundI {
  // takes too long to wake up
  window.Howler.autoSuspend = false;

  const defaultSoundSet: SoundSet = 'shogi';
  const defaultClockSoundSet: ClockSoundSet = 'system';
  const state = {
    sounds: new Map<string, Howl>(),
    soundSet: (document.body.dataset.soundSet || defaultSoundSet) as SoundSet,
    clockSoundSet: (document.body.dataset.clockSoundSet || defaultClockSoundSet) as ClockSoundSet,
    volumeStorage: storage.make('sound-volume'),
    baseUrl: assetUrl('sound', { noVersion: false }),
  };

  if (state.soundSet === 'speech' && !window.speechSynthesis?.getVoices().length)
    state.soundSet = defaultSoundSet;

  function enabled(categ: SoundCateg) {
    return (
      volume() !== 0 &&
      (categ === 'clock' ? state.clockSoundSet !== 'silent' : state.soundSet !== 'silent')
    );
  }

  function setForSoundCateg(categ: SoundCateg): string {
    return categ === 'system' ? state.soundSet : categ === 'clock' ? state.clockSoundSet : '';
  }

  function soundPath(name: string, categ: SoundCateg, set?: string): string {
    return [categ, set || setForSoundCateg(categ), name].filter(s => s).join('/');
  }

  function soundSet(s?: SoundSet): SoundSet {
    if (s) state.soundSet = s;
    return state.soundSet;
  }

  function clockSoundSet(s?: ClockSoundSet): ClockSoundSet {
    if (s) state.clockSoundSet = s;
    return state.clockSoundSet;
  }

  function clockSoundJapanese(): boolean {
    return ['chisei_mazawa', 'ippan_dansei', 'robot_ja', 'sakura_ajisai', 'shougi_sennin'].includes(
      state.clockSoundSet,
    );
  }

  function volume(value?: number) {
    if (defined(value)) state.volumeStorage.set(value);
    const v = Number.parseFloat(state.volumeStorage.get() || '');
    return v >= 0 ? v : 0.7;
  }

  function loadSound(name: string, categ: SoundCateg, set?: string): Howl | undefined {
    if (!enabled(categ)) return;

    const path = soundPath(name, categ, set);
    const cur = state.sounds.get(path);
    if (cur) return cur;

    const howl = new window.Howl({
      src: ['ogg', 'mp3'].map(ext => `${state.baseUrl}/${ext}/${path}.${ext}`),
    });
    state.sounds.set(path, howl);

    return howl;
  }

  function loadGameSounds(clock = false): void {
    if (!enabled) return;

    if (state.soundSet !== 'speech') {
      ['move', 'capture'].forEach(s => {
        loadSound(s, 'system');
      });
    }

    if (clock) {
      setTimeout(() => {
        if (state.clockSoundSet === 'system') loadSound('tick', 'clock');
        else {
          for (let i = 10; i > 0; i--) {
            loadSound(i.toString(), 'clock');
          }
        }
        ['low-time', 'byoyomi'].forEach(s => {
          loadSound(s, 'clock');
        });
      }, 1000 * 3);
    }
  }

  function play(name: string, categ?: SoundCateg, volumeOverride?: number): void {
    if (!enabled(categ || 'system')) return;

    categ = categ || 'system';

    let set = setForSoundCateg(categ);
    // speech only handles moves
    if (categ === 'system' && state.soundSet === 'speech') {
      if (['move', 'capture'].includes(name)) return;
      else set = defaultSoundSet;
    }

    window.Howler.volume(volumeOverride || volume());

    const howl = loadSound(name, categ, set);
    if (howl) {
      if (window.Howler.ctx?.state === 'suspended') {
        window.Howler.ctx.resume().then(() => {
          howl.play();
        });
      } else howl.play();
    } else console.warn('Howl not found', name, categ, set);
  }

  function playOnce(name: string, categ?: SoundCateg, volumeOverride?: number): void {
    const store = storage.make('just-played2');

    const attemptPlay = () => {
      const now = Date.now();
      const lastPlayed = Number.parseInt(store.get() || '0', 10);

      if (now - lastPlayed < 2000) return;

      store.set(`${now}`);
      play(name, categ, volumeOverride);
    };

    if (document.hasFocus()) {
      attemptPlay();
    } else {
      setTimeout(attemptPlay, 50 + Math.random() * 300);
    }
  }

  function move(capture?: boolean): void {
    if (enabled('system') && state.soundSet !== 'speech') {
      if (capture) play('capture');
      else play('move');
    }
  }

  function countdown(number: number): void {
    if (enabled('clock')) {
      if (state.clockSoundSet === 'system') play('tick', 'clock');
      else play(number.toString(), 'clock');
    }
  }

  function say(texts: { en?: string; ja?: string }, cut = false, force = false) {
    try {
      if ((state.soundSet !== 'speech' && !force) || volume() === 0) return false;

      const useJa = !!texts.ja && useJapanese();
      const text = useJa ? texts.ja : texts.en;
      const lang = useJa ? 'ja-JP' : 'en-US';

      const msg = new SpeechSynthesisUtterance(text);
      msg.volume = volume();
      msg.lang = lang;

      if (cut) speechSynthesis.cancel();
      speechSynthesis.speak(msg);

      console.log(`%c${msg.text}`, 'color: blue');

      return true;
    } catch (err) {
      console.error(err);
      return false;
    }
  }

  return {
    play,
    playOnce,
    move: throttle(100, (o?: boolean) => move(o)),
    countdown,
    volume,
    soundSet,
    clockSoundSet,
    clockSoundJapanese,
    loadSound,
    loadGameSounds,
    say,
  };
}
