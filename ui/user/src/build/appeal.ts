import { setupTranslator } from 'i18n/translator';

window.lishogi.ready.then(() => {
  setupTranslator('.appeal__msg__text');
});
