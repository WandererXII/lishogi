import { useJapanese } from 'common/common';
import { icons } from 'common/icons';
import { debounce } from 'common/timings';
import { i18n } from 'i18n';
import { h, type VNode } from 'snabbdom';
import { bind, type Close, header } from './util';

interface Sound {
  key: SoundSet | ClockSoundSet;
  en: string;
  ja: string;
}

type Tab = 'system' | 'clock';

export interface SoundData {
  system: Sound[];
  clock: Sound[];
}

export interface SoundCtrl {
  data: SoundData;
  activeTab: Tab;
  setActiveTab(t: Tab): void;
  set(k: SoundSet): void;
  setClock(k: ClockSoundSet): void;
  volume(v: number): void;
  redraw: Redraw;
  close: Close;
}

const soundApi = window.lishogi.sound;

export function ctrl(soundData: SoundData, redraw: Redraw, close: Close): SoundCtrl {
  return {
    data: soundData,
    activeTab: 'system',
    setActiveTab(t: Tab) {
      this.activeTab = t;
      this.redraw();
    },
    set(key: SoundSet) {
      soundApi.soundSet(key);
      window.lishogi.pubsub.emit('speech.enabled', key === 'speech');

      if (key === 'speech')
        soundApi.say({ en: 'Speech synthesis ready', ja: '音声合成の準備が整いました' });
      else soundApi.move();

      window.lishogi.xhr
        .text('POST', '/pref/soundSet', { formData: { set: key } })
        .catch(() => window.lishogi.announce({ msg: 'Failed to save sound preference' }));

      redraw();
    },
    setClock(key: ClockSoundSet) {
      soundApi.clockSoundSet(key);

      soundApi.play('low-time', 'clock');

      window.lishogi.xhr
        .text('POST', '/pref/clockSoundSet', { formData: { set: key } })
        .catch(() => window.lishogi.announce({ msg: 'Failed to save sound preference' }));

      redraw();
    },
    volume(v: number) {
      soundApi.volume(v);
      if (soundApi.soundSet() === 'speech')
        soundApi.say({ en: 'Volume set', ja: '音量が設定されました' });
      else soundApi.move();
    },
    redraw,
    close,
  };
}

export function view(ctrl: SoundCtrl): VNode {
  const current = ctrl.activeTab === 'system' ? soundApi.soundSet() : soundApi.clockSoundSet();
  const canSpeech = window.speechSynthesis?.getVoices().length;
  const list =
    ctrl.activeTab === 'system'
      ? ctrl.data.system.filter(s => s.key !== 'speech' || canSpeech)
      : ctrl.data.clock;

  return h(
    `div.sub.sound.${soundApi.soundSet()}`,
    {
      hook: {
        insert() {
          if (window.speechSynthesis) window.speechSynthesis.onvoiceschanged = ctrl.redraw;
        },
      },
    },
    [
      header(i18n('sound'), ctrl.close),
      h(
        'a.categ-tabs',
        {
          hook: bind('click', e => {
            const tab = ((e.target as HTMLElement).dataset.tab || 'standard') as Tab;
            ctrl.setActiveTab(tab);
          }),
        },
        (['system', 'clock'] as const).map(v =>
          h(
            'div',
            { attrs: { 'data-tab': v }, class: { active: ctrl.activeTab === v } },
            v === 'system' ? i18n('preferences:systemSound') : i18n('clock'),
          ),
        ),
      ),
      h('div.content', [
        h('div.selector', { key: ctrl.activeTab }, list.map(soundView(ctrl, current))),
        slider(ctrl),
      ]),
    ],
  );
}

function slider(ctrl: SoundCtrl): VNode {
  return h(
    'div.slider',
    h('input', {
      attrs: {
        id: 'sound-slider',
        type: 'range',
        min: 0,
        max: 1,
        step: 0.01,
      },
      hook: {
        insert: vnode => {
          const el = vnode.elm as HTMLInputElement;
          const setVolume = debounce(ctrl.volume, 300);

          el.value = soundApi.volume().toString();
          el.addEventListener('input', _ => {
            const value = Number.parseFloat(el.value);
            setVolume(value);
          });
          el.addEventListener('mouseout', _ => el.blur());
        },
      },
    }),
  );
}

const credits: Partial<Record<SoundSet | ClockSoundSet, string>> = {
  chisei_mazawa: 'https://www.youtube.com/c/chisei',
  sakura_ajisai: 'https://youtube.com/@Sakura_Ajisai',
  ippan_dansei: 'https://www.youtube.com/channel/UCoEQgBLlacPU18FoWDJA5Qg',
  shougi_sennin: 'https://www.youtube.com/channel/UCoEQgBLlacPU18FoWDJA5Qg',
};

function soundView(ctrl: SoundCtrl, current: string) {
  return (s: Sound) =>
    h(
      'a.text',
      {
        hook: bind('click', () =>
          ctrl.activeTab === 'system'
            ? ctrl.set(s.key as SoundSet)
            : ctrl.setClock(s.key as ClockSoundSet),
        ),
        class: { active: current === s.key },
      },
      [
        useJapanese() ? s.ja : s.en,
        credits[s.key]
          ? h('a.credit-link', {
              hook: bind('click', e => {
                e.stopPropagation();
              }),
              attrs: {
                'data-icon': icons.link,
                title: credits[s.key]!,
                href: credits[s.key]!,
                target: '_blank',
              },
            })
          : undefined,
      ],
    );
}
