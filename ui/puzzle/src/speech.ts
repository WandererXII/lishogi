import { loadLishogiScript } from 'common/assets';

export function setup(): void {
  window.lishogi.pubsub.on('speech.enabled', onSpeechChange);
  onSpeechChange(window.lishogi.sound.soundSet() === 'speech');
}

function onSpeechChange(enabled: boolean): void {
  if (!window.lishogi.modules.speech && enabled) loadLishogiScript('speech');
  else if (window.lishogi.modules.speech && !enabled) window.lishogi.modules.speech = undefined;
}

export function node(n: Tree.Node, cut: boolean): void {
  if (window.lishogi.modules.speech) window.lishogi.modules.speech({ notation: n.notation, cut });
}

export function failure(): void {
  if (window.lishogi.modules.speech)
    window.lishogi.sound.say({ en: 'Failed!', ja: '失敗！' }, false);
}

export function success(): void {
  if (window.lishogi.modules.speech)
    window.lishogi.sound.say({ en: 'Success!', ja: '成功！' }, false);
}
