import { spinnerHtml } from 'common/spinner';
import { debounce } from 'common/timings';

window.lishogi.ready.then(() => {
  const $editor = $('.coach-edit');

  $editor.find('.tabs > div').on('click', function () {
    $editor.find('.tabs > div').removeClass('active');
    $(this).addClass('active');
    $editor.find('.panel').removeClass('active');
    $editor.find(`.panel.${$(this).data('tab')}`).addClass('active');
    $editor.find('div.status').removeClass('saved');
  });

  $('.coach_picture form.upload input[type=file]').on('change', function () {
    $('.picture_wrap').html(spinnerHtml);
    $(this).parents('form').trigger('submit');
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

  const submit = debounce(() => {
    const form = document.querySelector('form.async') as HTMLFormElement;
    if (!form) return;
    window.lishogi.xhr.formToXhr(form).then(() => {
      $editor.find('div.status').addClass('saved');
    });
  }, 1200);

  setTimeout(() => {
    $editor.find('input, textarea, select').on('input paste change keyup', () => {
      const $statusDiv = $editor.find('div.status');
      $statusDiv.removeClass('saved');
      submit();
    });
  }, 0);
});
