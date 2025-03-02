import { type Prop, prop } from 'common/common';
import { type BackgroundCtrl, type BackgroundData, ctrl as backgroundCtrl } from './background';
import { type CustomBackgroundCtrl, ctrl as customBackgroundCtrl } from './custom-background';
import {
  type CustomThemeCtrl,
  type CustomThemeData,
  ctrl as customThemeCtrl,
} from './custom-theme';
import { type LangsCtrl, type LangsData, ctrl as langsCtrl } from './langs';
import { type NotationCtrl, type NotationData, ctrl as notationCtrl } from './notation';
import { type PieceCtrl, type PieceSetData, ctrl as pieceCtrl } from './piece';
import { type PingCtrl, ctrl as pingCtrl } from './ping';
import { type SoundCtrl, type SoundData, ctrl as soundCtrl } from './sound';
import { type ThemeCtrl, type ThemeData, ctrl as themeCtrl } from './theme';

interface DasherData {
  user?: LightUser;
  lang: LangsData;
  notation: NotationData;
  sound: SoundData;
  background: BackgroundData;
  theme: ThemeData;
  customTheme: CustomThemeData;
  piece: PieceSetData;
  chuPiece: PieceSetData;
  kyoPiece: PieceSetData;
  inbox: boolean;
  coach: boolean;
  streamer: boolean;
}

export type Mode =
  | 'links'
  | 'langs'
  | 'sound'
  | 'background'
  | 'customBackground'
  | 'board'
  | 'notation'
  | 'theme'
  | 'customTheme'
  | 'piece';

const defaultMode = 'links';

export interface DasherCtrl {
  mode: Prop<Mode>;
  setMode(m: Mode): void;
  data: DasherData;
  ping: PingCtrl;
  subs: {
    langs: LangsCtrl;
    sound: SoundCtrl;
    background: BackgroundCtrl;
    customBackground: CustomBackgroundCtrl;
    notation: NotationCtrl;
    theme: ThemeCtrl;
    customTheme: CustomThemeCtrl;
    piece: PieceCtrl;
  };
  opts: DasherOpts;
}

export interface DasherOpts {
  playing: boolean;
}

export function makeCtrl(opts: DasherOpts, data: DasherData, redraw: Redraw): DasherCtrl {
  const mode: Prop<Mode> = prop(defaultMode as Mode);

  function setMode(m: Mode) {
    mode(m);
    redraw();
  }
  function close() {
    setMode(defaultMode);
  }

  const ping = pingCtrl(redraw);

  const subs = {
    langs: langsCtrl(data.lang, close),
    sound: soundCtrl(data.sound, redraw, close),
    background: backgroundCtrl(data.background, redraw, setMode, close),
    customBackground: customBackgroundCtrl(data.background.customBackground, redraw, setMode),
    theme: themeCtrl(data.theme, redraw, setMode),
    customTheme: customThemeCtrl(data.customTheme, redraw, setMode),
    notation: notationCtrl(data.notation, redraw, close),
    piece: pieceCtrl(data.piece, data.chuPiece, data.kyoPiece, redraw, close),
  };

  window.lishogi.pubsub.on('top.toggle.user_tag', () => setMode(defaultMode));

  return {
    mode,
    setMode,
    data,
    ping,
    subs,
    opts,
  };
}
