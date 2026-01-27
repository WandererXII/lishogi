export {};

declare global {
  type SoundCateg = 'system' | 'clock' | 'misc';

  type SoundSet = 'shogi' | 'shogialt' | 'chess' | 'nes' | 'sfx' | 'speech' | 'silent';

  type ClockSoundSet =
    | 'system'
    | 'chisei_mazawa'
    | 'eigo'
    | 'ippan_dansei'
    | 'robot_en'
    | 'robot_ja'
    | 'sakura_ajisai'
    | 'shougi_sennin'
    | 'silent';

  interface SoundI {
    play: (name: string, categ?: SoundCateg, volumeOverride?: number) => void;
    playOnce: (name: string, categ?: SoundCateg, volumeOverride?: number) => void;
    move: (capture?: boolean) => void;
    countdown: (number: number) => void;
    volume: (value?: number) => number;
    soundSet: (value?: SoundSet) => SoundSet;
    clockSoundSet: (value?: ClockSoundSet) => ClockSoundSet;
    clockSoundJapanese: () => boolean;
    loadGameSounds: (clock?: boolean) => void;
    loadSound: (name: string, categ: SoundCateg, set?: string) => void;
    say: (texts: { en?: string; ja?: string }, cut?: boolean, force?: boolean) => boolean;
  }

  interface LishogiSpeech {
    notation: string | undefined;
    cut: boolean;
  }
}
