import { initImageUpload } from 'common/image-upload';
import { i18n } from 'i18n';

window.lishogi.ready.then(() => {
  const $editor = $('.coach-edit');

  $editor.find('.tabs > div').on('click', function () {
    $editor.find('.tabs > div').removeClass('active');
    $(this).addClass('active');
    $editor.find('.panel').removeClass('active');
    $editor.find(`.panel.${$(this).data('tab')}`).addClass('active');
  });

  const langInput = document.getElementById('form3-languages') as HTMLInputElement;
  if (langInput) {
    const whitelistJson = langInput.getAttribute('data-all');
    const whitelist = whitelistJson ? (JSON.parse(whitelistJson) as Tagify.TagData[]) : undefined;
    const initialValues = langInput
      .getAttribute('data-value')
      ?.split(',')
      .map(code => whitelist?.find(l => l.code == code)?.value)
      .filter(v => !!v);
    if (initialValues) langInput.setAttribute('value', initialValues.join(','));
    new window.Tagify(langInput, {
      maxTags: 10,
      whitelist,
      enforceWhitelist: true,
      dropdown: {
        enabled: 1,
      },
    });
  }

  let modified = false;
  setTimeout(() => {
    $editor.find('input, textarea, select').on('input paste change keyup', () => {
      modified = true;
    });
  }, 0);
  setTimeout(() => {
    $editor.find('form').on('submit', () => {
      modified = false;
    });
  }, 0);

  window.addEventListener('beforeunload', e => {
    if (!modified) return;
    e.preventDefault();
  });

  initImageUpload({
    selector: '#image-editor',
    inputSelector: '#form3-picturePath',
    label: i18n('chooseFileOrDrag'),
  });
});
