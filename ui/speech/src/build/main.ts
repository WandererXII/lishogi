import { renderMoveOrDrop as enRender } from '../english';
import { renderMoveOrDrop as jpRender, numberToSpeech as jpNum } from '../japanese';

function main(opts: LishogiSpeech): boolean {
  if (opts.notation !== undefined) {
    return window.lishogi.sound.say(
      {
        en: opts.notation ? enRender(opts.notation) : 'Game start',
        jp: opts.notation ? jpRender(opts.notation) : '開始',
      },
      opts.cut ?? false,
      false,
      opts.forceJapanese ?? false
    );
  } else if (opts.byoyomiCount !== undefined) {
    return window.lishogi.sound.say(
      {
        en: opts.byoyomiCount.toString(),
        jp: jpNum(opts.byoyomiCount),
      },
      opts.cut ?? false,
      true,
      opts.forceJapanese ?? false
    );
  }

  return false;
}

window.lishogi.registerModule(__bundlename__, main);
